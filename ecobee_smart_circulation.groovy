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
 *	0.1.1	01/25/2017	Barry Burke Initial Release	
 */
def getVersionNum() { return "0.1.1" }
private def getVersionLabel() { return "ecobee smartZones Version ${getVersionNum()}" }


definition(
	name: "ecobee Smart Circulation",
	namespace: "smartthings",
	author: "Barry A. Burke (storageanarchy at gmail dot com)",
	description: "If a larger than configured delta is found between sensors the fan circulation time will be automatically adjusted.",
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
	dynamicPage(name: "mainPage", title: "Setup Routines", uninstall: true, install: true) {
    	section(title: "Name for Smart Circulation Handler") {
        	label title: "Name this Smart Circulation Handler", required: true
        
        }
        
        section(title: "Select Thermostat") {
        	if(settings.tempDisable == true) paragraph "WARNING: Temporarily Disabled as requested. Turn back on to activate handler."
        	input (name: "theThermostat", type:"capability.thermostat", title: "Pick Ecobee Thermostat", required: true, 
				   multiple: false, submitOnChange: true)            
		}
        section(title: "Circulation Time Settings") {
            input (name: "minFanOnTime", type: "number", title: "Set minimum circulation minutes per hour", required: true,
				   defaultValue: "5", description: "5", range: "0..55")
            input (name: "maxFanOnTime", type: "number", title: "Set maximum circulation minutes per hour", required: true,
				   defaultValue: "55", description: "55", range: "0..55")
            input (name: "fanOnTimeDelta", type: "number", title: "Set circulation time adjustment (minutes)", required: true,
				   defaultValue: "5", description: "5", range: "1..20")
            input (name: "fanAdjustMinutes", type: "number", title: "Adjustment period (minutes)", required: true,
				   defaultValue: "60", description: "60")
        }
        
        section(title: "Select Temperature Sensors") {
            input(name: "theSensors", title: "Pick temperature sensor(s)", type: "capability.temperatureMeasurement", 
				  required: true, multiple: true, submitOnChange: true)
		}
		
		section(title: "Max Temperature Delta" ){
            input(name: "deltaTemp", type: "decimal", title: "Enter temperature delta for making adjustments", required: true,
				  defaultValue: "2.0", description: "2.0"/*, range "1..9"*/)
        }
       
		section([mobileOnly:true]) {
            mode title: "Enable for specific mode(s)", required: false
        }
		
		section(title: "Temporarily Disable?") {
        	input(name: "tempDisable", title: "Temporarily Disable Handler? ", type: "bool", required: false, description: "", submitOnChange: true)                
        }
        
        section (getVersionLabel()) {}
    }
}

// Main functions
def installed() {
	LOG("installed() entered", 5)
    
	initialize()  
}

def updated() {
	LOG("updated() entered", 5)
	unsubscribe()
    initialize()
}

def initialize() {
	LOG("initialize() entered")

	// Now, just exit if we are disabled...
	if(tempDisable == true) {
    	LOG("Temporarily Disabled as per request.", 2, null, "warn")
    	return true
    }
    // Initialize state as if we haven't checked in more than fanAdjustMinutes
    atomicState.lastAdjustment = now() - (60001 * settings.fanAdjustMinutes.toLong()).toLong() // make sure we run on next deltaHandler event
    
    subscribe(theThermostat, "thermostatOperatingState", deltaHandler)
    log.debug "theThermostat: ${theThermostat}, fanMinOnTime: ${theThermostat.currentValue('fanMinOnTime')}"
    subscribe(theSensors, "temperature", deltaHandler)

	// we'll use these as watchdogs - "random" events that occur during long idle periods (e.g., home is in Away or Night mode
    subscribe(location, "mode", deltaHandler)
    subscribe(location, "routineExecuted", deltaHandler)
    subscribe(location, "sunset", deltaHandler)
    subscribe(location, "sunrise", deltaHandler)
    subscribe(location, "position", deltaHandler)

	if (theThermostat.currentValue("fanMinOnTime").toInteger() < settings.minFanOnTime) settings.theThermostat.setFanMinOnTime( settings.minFanOnTime )
    
    runEvery5Minutes(deltaHandler)		// set up a regular poll, just in case things get too quiet :)
    deltaHandler()
    LOG("initialize() complete")
}

def deltaHandler(evt=null) {
    LOG("temperatureHandler() entered with evt: ${evt}", 5)
    
    if (atomicState.amIRunning) {return} else {atomicState.amIRunning = true}
    
    def temps = [] 
    theSensors.each {
    	def temp = it.currentValue("temperature")
    	if (temp.isNumber() ) temps += [temp]
    }
    log.debug "temps: ${temps}" 
    if (temps.size() < 2) {				// ignore if we don't have enough valid data
    	atomicState.amIRunning = false
        return 
    }
    // Makes no sense to change fanMinOnTime while heating or cooling is running - take action ONLY on events while idle or fan is running
    def statState = theThermostat.currentValue("thermostatOperatingState")
    if ((statState != 'idle')&&(statState != 'fan only')) {
    	log.debug("${theThermostat} is ${statState}, no adjustments made")
        atomicState.amIRunning = false
        return
    }
    
    if (atomicState.lastAdjustment) {
        def timeNow = now()
        if (timeNow <= (atomicState.lastAdjustment + (60000 * settings.fanAdjustMinutes))) {
            log.debug("Not time to adjust yet")
            atomicState.amIRunning = false
            return
		}
	}
    
	Double min = temps.min().toDouble()
	Double max = temps.max().toDouble()
	Double delta = max - min
    
	Integer currentOnTime = theThermostat.currentValue("fanMinOnTime").toInteger()
	Integer newOnTime = currentOnTime
	
	if (delta > settings.deltaTemp.toDouble()) {			// need longer recirculation
		newOnTime = currentOnTime + settings.fanOnTimeDelta
		if (newOnTime > settings.maxFanOnTime) {
			newOnTime = settings.maxFanOnTime
		}
		if (currentOnTime != newOnTime) {
			log.debug "Delta is ${String.format("%.2f",delta)}/${String.format("%.2f",settings.deltaTemp)}, increasing circulation time for ${theThermostat} to ${newOnTime} minutes"
			theThermostat.setFanMinOnTime(newOnTime)
			atomicState.lastAdjustment = now()
            atomicState.amIRunning = false
            return
		}
	} else {
        Double target = (getTemperatureScale() == "C") ? 0.5556 : 1.0
        if (target > settings.deltaTemp) target = settings.deltaTemp * 0.95	// arbitrary - we have to be less than deltaTemp
    	if (delta <= target) {			// start adjusting back downwards once we get within 1F or .5C
			newOnTime = currentOnTime - settings.fanOnTimeDelta
			if (newOnTime < settings.minFanOnTime) {
				newOnTime = settings.minFanOnTime
			}
            if (currentOnTime != newOnTime) {
           		log.debug "Delta is ${String.format("%.2f",delta)}/${String.format("%.2f",target)}, decreasing circulation time for ${theThermostat} to ${newOnTime} minutes"
				theThermostat.setFanMinOnTime(newOnTime)
				atomicState.lastAdjustment = now()
                atomicState.amIRunning = false
                return
            }
		}
	}
	log.debug "No adjustment required"
    atomicState.amIRunning = false
}

// Helper Functions
private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	parent.LOG(message, level, child, logType, event, displayEvent)
}
