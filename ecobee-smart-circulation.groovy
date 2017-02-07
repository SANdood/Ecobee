/**
 *  ecobee smartCirculation
 *
 *  Copyright 2017 Barry A. Burke
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0  
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	0.1.1	01/25/2017	Barry Burke -	Initial Release
 *	0.1.2	01/28/2017	Barry Burke	-	Beta Release
 *	0.1.3	01/29/2017	Barry Burke -	Added configurable support for overriding fanMinOnTime during Vacation holds
 *	0.1.4	02/04/2017 	Barry Burke	-	Added ability to specify both modes and programsList for when a handler should/can run
 *	0.1.5	0s/06/2017	Barry Burke -	Android fix (bad range in min/maxFanOnTime settings)
 *
 */
def getVersionNum() { return "0.1.5" }
private def getVersionLabel() { return "ecobee Smart Circulation Version ${getVersionNum()}" }
import groovy.json.JsonSlurper

definition(
	name: "ecobee Smart Circulation",
	namespace: "smartthings",
	author: "Barry A. Burke (storageanarchy at gmail dot com)",
	description: "If a larger than configured temperature delta is found between 2 or more sensors, the minimum Fan On minutes per hour (m/hr) will be automatically adjusted.",
	category: "Convenience",
	parent: "smartthings:Ecobee (Connect)",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: false
)

preferences {
	page(name: "mainPage")
}

// Preferences Pages
def mainPage() {
	dynamicPage(name: "mainPage", title: "Configure Smart Circulation", uninstall: true, install: true) {
    	section(title: "Name for Smart Circulation Handler") {
        	label title: "Name this Smart Circulation Handler", required: true      
        }
        
        section(title: "Select Thermostat") {
        	if(settings.tempDisable == true) paragraph "WARNING: Temporarily Disabled as requested. Turn back on to activate handler."
        	input(name: "theThermostat", type:"capability.Thermostat", title: "Use which Ecobee Thermostat", required: true, multiple: false, submitOnChange: true)            
		}
                
        section(title: "Select Temperature Sensors") {
            input(name: "theSensors", title: "Use which temperature sensor(s)", type: "capability.temperatureMeasurement", required: true, multiple: true, submitOnChange: true)
		}
        
       	section(title: "Fan On Time Automation Configuration") {
        	paragraph("Increase Circulation time (min/hr) when the difference between the maximum and the minimum temperature reading of the above sensors is more than this.")
            input(name: "deltaTemp", type: "enum", title: "Select temperature delta", required: true, defaultValue: "2.0", multiple:false, options:["1.0", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0", "7.5", "10.0"])
            paragraph("Minimum Circulation time (min/hr). Includes heating, cooling and fan only minutes.")
            input(name: "minFanOnTime", type: "number", title: "Set minimum fan on min/hr (0-${maxFanOnTime?maxFanOnTime:55})", required: true, defaultValue: "5", description: "5", range: "0..${maxFanOnTime?maxFanOnTime:55}", submitOnChange: true)
            paragraph("Maximum Circulation time (min/hr).")
            input(name: "maxFanOnTime", type: "number", title: "Set maximum fan on min/hr (${minFanOnTime?minFanOnTime:5}-55)", required: true, defaultValue: "55", description: "55", range: "${minFanOnTime?minFanOnTime:5}..55", submitOnChange: true)
            paragraph("Adjust Circulation time (min/hr) by this many minutes each adjustment.")
            input(name: "fanOnTimeDelta", type: "number", title: "Minutes per adjustment (1-20)", required: true, defaultValue: "5", description: "5", range: "1..20")
            paragraph("Minimum number of minutes between adjustments.")
            input(name: "fanAdjustMinutes", type: "number", title: "Time adjustment frequency in minutes (5-60)", required: true, defaultValue: "10", description: "15", range: "5..60")
        }
       
        section(title: "Vacation Hold Override") {
        	paragraph("The thermostat's Circulation setting is overridden when a Vacation is in effect. If you would like to automate the Circulation time during a Vacation hold, enable this setting.")
            input(name: "vacationOverride", type: "bool", title: "Override fan during Vacation hold?", defaulValue: false)
        }
       
		section(title: "Enable only for specific modes or programs?") {
        	paragraph("Circulation time (min/hr) is only adjusted while in these modes and/or programs. The time will remain at the last setting while in other modes. If you want different circulation times for other modes or programs, create multiple Smart Circulation handlers.")
            input(name: "theModes",type: "mode", title: "Only when the Location Mode is", multiple: true, required: false)
            input(name: "programs", type: "enum", title: "Only when the ${theThermostat ? theThermostat : 'thermostat'} Program is", multiple: true, required: false, options: getProgramsList())
        }
		
		section(title: "Temporarily Disable?") {
        	input(name: "tempDisable", title: "Temporarily Disable Handler? ", type: "bool", required: false, description: "", submitOnChange: true)                
        }
        
        section (getVersionLabel())
    }
}

// Main functions
def installed() {
	LOG("installed() entered", 4, "", 'trace')
    
    // initialize the min/max trackers...plan to use these to optimize the decrease cycles
    atomicState.maxMax = 0.0
    atomicState.minMin = 100.0
    atomicState.maxDelta = 0.0
    atomicState.minDelta = 100.0    
	initialize()  
}

def updated() {
	LOG("updated() entered", 4, "", 'trace')
	unsubscribe()
    unschedule()
    initialize()
}

def getProgramsList() {
    return settings.theThermostat ? new JsonSlurper().parseText(theThermostat.currentValue('programsList')) : ["Away","Home","Sleep"]
}

def initialize() {
	LOG("${getVersionLabel()}\nInitializing...", 3, "", 'info')
	atomicState.amIRunning = false				// reset in case we get stuck (doesn't matter a lot if we run more than 1 instance, just wastes resources)
    def mode = location.mode
    
	// Now, just exit if we are disabled...
	if(tempDisable == true) {
    	LOG("temporarily disabled as per request.", 2, null, "warn")
    	return true
    }
    
    // Initialize as if we haven't checked in more than fanAdjustMinutes
    atomicState.lastAdjustmentTime = now() - (60001 * settings.fanAdjustMinutes.toLong()).toLong() // make sure we run on next deltaHandler event
    
    subscribe(settings.theThermostat, "thermostatOperatingState", deltaHandler)
    subscribe(settings.theThermostat, "currentProgram", deltaHandler)
    subscribe(settings.theThermostat, "thermostatHold", deltaHandler)
    subscribe(settings.theSensors, "temperature", deltaHandler)
    subscribe(location, "mode", deltaHandler)
    subscribe(location, "routineExecuted", deltaHandler)
    
    Integer currentOnTime = settings.theThermostat.currentValue('fanMinOnTime').toInteger()
    boolean vacationHold = (settings.theThermostat.currentValue("currentProgramName") == "Vacation")

	def programsList = []
    programsList = new JsonSlurper().parseText(theThermostat.currentValue('programsList'))
    
	log.debug "settings ${theModes}, location ${location.mode}, programs ${programs} & ${programsList}, thermostat ${theThermostat.currentValue('currentProgram')}"
    log.debug "programs list contains current program: ${programsList.contains(theThermostat.currentValue('currentProgram'))}"
    log.debug "${programsList[0]}, ${programsList[1]} ${programsList.size()}"
   
	// only adjust if we are currently in one of the allowed modes
    def isOK = true
	if (settings.theModes && !settings.theModes.contains(location.mode)) isOK = false
    if (isOK && settings.programs && !settings.programs?.contains(theThermostat.currentValue('currentProgram'))) isOK = false
    
    if (isOK) {	
		if (currentOnTime < settings.minFanOnTime) {
    		if (vacationHold && settings.vacationOverride) {
        		settings.theThermostat.setVacationFanMinOnTime(settings.minFanOnTime)
            	currentOnTime = settings.minFanOnTime
        	} else if (!vacationHold) {
    			settings.theThermostat.setFanMinOnTime(settings.minFanOnTime)
            	currentOnTime = settings.minFanOnTime
        	}
    	} else if (currentOnTime > settings.maxFanOnTime) {
    		if (vacationHold && settings.vacationOverride) {
        		settings.theThermostat.setVacationFanMinOnTime(settings.maxFanOnTime)
        		currentOnTime = settings.maxFanOnTime
        	} else if (!vacationHold) {
    			settings.theThermostat.setFanMinOnTime(settings.maxFanOnTime)
        		currentOnTime = settings.maxFanOnTime
        	}
    	}
        if (!vacationHold) deltaHandler()
    }
    def vaca = vacationHold ? " is in Vacation mode, " : " "    
    LOG("thermostat ${settings.theThermostat}${vaca}circulation time is now ${currentOnTime} minutes/hour",2,"",'info')
	atomicState.fanSinceLastAdjustment = true    
    LOG("Initialization complete", 4, "", 'trace')
}

def deltaHandler(evt=null) {
	def skipIt = false
	// silently refuse to run if not in proper mode or program
	if (settings.theModes && !settings.theModes?.contains(location.mode)) skipIt = true  	
    if (settings.programs && !settings.programs?.contains(theThermostat.currentValue('currentProgram'))) skipIt = true
    if (skipIt) {
    	atomicState.amIRunning = false
        return
    }
    
	def vacationHold = (settings.theThermostat.currentValue("currentProgramName") == "Vacation")
	if (!settings.vacationOverride && vacationHold) {
    	LOG("${settings.theThermostat} is in Vacation mode, but not configured to override Vacation fanMinOnTime, returning", 4, "", 'warn')
        atomicState.amIRunning = false
        return
    }
    
	if (evt) {
        if ((evt.name == "thermostatOperatingState") && !atomicState.fanSinceLastAdjustment) {
    		if ((evt.value != 'idle') && (!evt.value.contains('ending'))) atomicState.fanSinceLastAdjustment = true
    	}
        LOG("deltaHandler() entered with event ${evt.name}: ${evt.value}", 4, "", 'trace')
    } else {
    	LOG("deltaHandler() called directly", 4, "", 'trace')
    }

	// reset the amIRunning sequencer if it gets hung for more than an hour
	if (atomicState.lastCheckTime && ((now() - atomicState.lastCheckTime) > 3600000)) atomicState.amIRunning = false
    if (atomicState.amIRunning) {return} else {atomicState.amIRunning = true}
    atomicState.lastCheckTime = now()
    
    // parse temps - ecobee sensors can return "unknown", others may return
    def temps = []
    Double total = 0.0
    def i=0
    settings.theSensors.each {
    	def temp = it.currentValue("temperature")
    	if (temp.isNumber() && (temp > 0)) {
        	temps += [temp]	// we want to deal with valid inside temperatures only
            total = total + temp.toDouble()
            i = i + 1
        }
    }
    Double avg = total / i.toDouble()
    
    LOG("Current temperature readings: ${temps}, average is ${String.format("%.3f",avg)}", 4, "", 'trace')
    if (temps.size() < 2) {				// ignore if we don't have enough valid data
    	LOG("Only recieved ${temps.size()} valid temperature readings, skipping...",3,"",'warn')
    	atomicState.amIRunning = false
        return 
    }
    
    Double min = temps.min().toDouble().round(2)
	Double max = temps.max().toDouble().round(2)
	Double delta = (max - min).round(2)
    
    atomicState.maxMax = atomicState.maxMax.toDouble() > max ? atomicState.maxMax: max 
    atomicState.minMin = atomicState.minMin.toDouble() < min ? atomicState.minMin: min
    atomicState.maxDelta = atomicState.maxDelta.toDouble() > delta ? atomicState.maxDelta: delta 
    atomicState.minDelta = atomicState.minDelta.toDouble() < delta ? atomicState.minDelta: delta
    
    // Makes no sense to change fanMinOnTime while heating or cooling is running - take action ONLY on events while idle or fan is running
    def statState = settings.theThermostat.currentValue("thermostatOperatingState")
    if ((statState != 'idle') && (statState != 'fan only')) {
    	LOG("${settings.theThermostat} is ${statState}, no adjustments made", 4, "", 'trace' )
        atomicState.amIRunning = false
        return
    }

    if (atomicState.lastAdjustmentTime) {
        def timeNow = now()
        def minutesLeft = settings.fanAdjustMinutes - ((timeNow - atomicState.lastAdjustmentTime) / 60000).toInteger()
        if (minutesLeft >0) {
            LOG("Not time to adjust yet - ${minutesLeft} minutes left",4,'','trace')
            atomicState.amIRunning = false
            return
		}
	}
    
    Integer currentOnTime = settings.theThermostat.currentValue('fanMinOnTime').toInteger()	// Ecobee (Connect) will populate this with Vacation.fanMinOnTime if necessary
	Integer newOnTime = currentOnTime
	
	if (delta >= settings.deltaTemp.toDouble()) {			// need to increase recirculation (fanMinOnTime)
		newOnTime = currentOnTime + settings.fanOnTimeDelta
		if (newOnTime > settings.maxFanOnTime) {
			newOnTime = settings.maxFanOnTime
		}
		if (currentOnTime != newOnTime) {
			LOG("Temperature delta is ${String.format("%.2f",delta)}/${settings.deltaTemp}, increasing circulation time for ${settings.theThermostat} to ${newOnTime} minutes",2,"",'info')
			if (vacationHold) {
            	settings.theThermostat.setVacationFanMinOnTime(newOnTime)
            } else {
            	settings.theThermostat.setFanMinOnTime(newOnTime)
            }
            atomicState.fanSinceLastAdjustment = false
			atomicState.lastAdjustmentTime = now()
            atomicState.amIRunning = false
            return
		}
	} else {
        Double target = (getTemperatureScale() == "C") ? 0.55 : 1.0
        //atomicState.target = target
        if (target > settings.deltaTemp.toDouble()) target = (settings.deltaTemp.toDouble() * 0.66667).round(2)	// arbitrary - we have to be less than deltaTemp
    	if (delta <= target) {			// start adjusting back downwards once we get within 1F or .5556C
			newOnTime = currentOnTime - settings.fanOnTimeDelta
			if (newOnTime < settings.minFanOnTime) {
				newOnTime = settings.minFanOnTime
			}
            if (currentOnTime != newOnTime) {
           		LOG("Temperature delta is ${String.format("%.2f",delta)}/${String.format("%.2f",target)}, decreasing circulation time for ${settings.theThermostat} to ${newOnTime} minutes",2,"",'info')
				if (vacationHold) {
                	settings.theThermostat.setVacationFanMinOnTime(newOnTime)
                } else {
                	settings.theThermostat.setFanMinOnTime(newOnTime)
                }
                atomicState.fanSinceLastAdjustment = false
				atomicState.lastAdjustmentTime = now()
                atomicState.amIRunning = false
                return
            }
		}
	}
	LOG("No adjustment required",4,"",'trace')
    atomicState.amIRunning = false
}

// Helper Functions
private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	parent.LOG(message, level, child, logType, event, displayEvent)
    if (level <= 4) log.info message
}
