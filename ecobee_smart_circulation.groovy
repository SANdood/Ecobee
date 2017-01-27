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
				   defaultValue: "5", description: "5", range: "0..55", submitOnChange: true)
            input (name: "maxFanOnTime", type: "number", title: "Set maximum circulation minutes per hour", required: true,
				   defaultValue: "55", description: "55", range: "${minFanOnTime}..55", submitOnChange: true)
            input (name: "fanOnTimeDelta", type: "number", title: "Set circulation time adjustment (minutes)", required: true,
				   defaultValue: "5", description: "5", range: "1..20")
            input (name: "fanAdjustMinutes", type: "number", title: "Adjustment period (minutes)", required: true,
				   defaultValue: "15", description: "15", range: "5..60")
        }
        
        section(title: "Select Temperature Sensors") {
            input(name: "theSensors", title: "Pick temperature sensor(s)", type: "capability.temperatureMeasurement", 
				  required: true, multiple: true, submitOnChange: true)
		}
		
		section(title: "Max Temperature Delta" ){
            input(name: "deltaTemp", type: "enum", title: "Select temperature delta for making adjustments", required: true,
				  defaultValue: "2.0", description: "2.0", multiple:false, options:["1.0", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0"])
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
	LOG("installed() entered", 4, "", 'trace')
    
	initialize()  
}

def updated() {
	LOG("updated() entered", 4, "", 'trace')
	unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
	LOG("Initializing...", 4, "", 'trace')
	atomicState.amIRunning = false				// reset in case we get stuck (doesn't matter a lot if we run more than 1 instance, just wastes resources)
    
	// Now, just exit if we are disabled...
	if(tempDisable == true) {
    	LOG("temporarily disabled as per request.", 2, null, "warn")
    	return true
    }
    // Initialize state as if we haven't checked in more than fanAdjustMinutes
    atomicState.lastAdjustment = now() - (60001 * settings.fanAdjustMinutes.toLong()).toLong() // make sure we run on next deltaHandler event
    
    // reset the min/max trackers...plan to use these to optimize the decrease cycles
    atomicState.maxMax = 0.0
    atomicState.minMin = 100.0
    atomicState.maxDelta = 0.0
    atomicState.minDelta = 100.0
    
    subscribe(settings.theThermostat, "thermostatOperatingState", deltaHandler)
    
    subscribe(settings.theSensors, "temperature", deltaHandler)
    subscribe(location, "mode", deltaHandler)
    subscribe(location, "routineExecuted", deltaHandler)

	Integer currentOnTime = settings.theThermostat.currentValue('fanMinOnTime').toInteger()
	if (currentOnTime < settings.minFanOnTime) {
    	settings.theThermostat.setFanMinOnTime(settings.minFanOnTime)
        currentOnTime = settings.minFanOnTime
    } else if (currentOnTime > settings.maxFanOnTime) {
    	settings.theThermostat.setFanMinOnTime(settings.maxFanOnTime)
        currentOnTime = settings.maxFanOnTime
    }
    LOG("thermostat ${settings.theThermostat} circulation time is now ${currentOnTime} minutes/hour",2,"",'info')
    
    deltaHandler()
    LOG("Initialization complete", 4, "", 'trace')
}

def deltaHandler(evt=null) {
	if (evt) {
    	LOG("deltaHandler() entered with evt: ${evt.name}, ${evt.value}", 4, "", 'trace')
    } else {
    	LOG("deltaHandler() called directly", 4, "", 'trace')
    }
    
    if (atomicState.amIRunning) {return} else {atomicState.amIRunning = true}
    
    // parse temps - ecobee sensors can return "unknown", others may return
    def temps = [] 
    settings.theSensors.each {
    	def temp = it.currentValue("temperature")
    	if (temp.isNumber() && (temp > 0)) temps += [temp]	// we want to deal with valid inside temperatures only
    }
    LOG("current temperature readings: ${temps}", 4, "", 'trace')
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
    
    if (atomicState.lastAdjustment) {
        def timeNow = now()
        if (timeNow <= (atomicState.lastAdjustment + (60000 * settings.fanAdjustMinutes))) {
        	def minutesLeft = settings.fanAdjustMinutes - ((timeNow - atomicState.lastAdjustment) / 60000).toInteger()
            LOG("Not time to adjust yet - ${minutesLeft} minutes left",4,'','trace')
            atomicState.amIRunning = false
            return
		}
	}
    
	Integer currentOnTime = settings.theThermostat.currentValue('fanMinOnTime').toInteger()
	Integer newOnTime = currentOnTime
	
	if (delta > settings.deltaTemp.toDouble()) {			// need to increase recirculation (fanMinOnTime)
		newOnTime = currentOnTime + settings.fanOnTimeDelta
		if (newOnTime > settings.maxFanOnTime) {
			newOnTime = settings.maxFanOnTime
		}
		if (currentOnTime != newOnTime) {
			LOG("Temperature delta is ${String.format("%.2f",delta)}/${settings.deltaTemp}, increasing circulation time for ${settings.theThermostat} to ${newOnTime} minutes",2,"",'info')
			settings.theThermostat.setFanMinOnTime(newOnTime)
			atomicState.lastAdjustment = now()
            atomicState.amIRunning = false
            return
		}
	} else {
        Double target = (getTemperatureScale() == "C") ? 0.5556 : 1.0
        //atomicState.target = target
        if (target > settings.deltaTemp.toDouble()) target = settings.deltaTemp.toDouble() * 0.95	// arbitrary - we have to be less than deltaTemp
    	if (delta <= target) {			// start adjusting back downwards once we get within 1F or .5556C
			newOnTime = currentOnTime - settings.fanOnTimeDelta
			if (newOnTime < settings.minFanOnTime) {
				newOnTime = settings.minFanOnTime
			}
            if (currentOnTime != newOnTime) {
           		LOG("Temperature delta is ${String.format("%.2f",delta)}/${String.format("%.2f",target)}, decreasing circulation time for ${settings.theThermostat} to ${newOnTime} minutes",2,"",'info')
				settings.theThermostat.setFanMinOnTime(newOnTime)
				atomicState.lastAdjustment = now()
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
}
