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
 */
def getVersionNum() { return "0.1.0" }
private def getVersionLabel() { return "ecobee smartZones Version ${getVersionNum()}" }

/*

 */

definition(
	name: "ecobee Smart Circulation",
	namespace: "smartthings",
	author: "Barry A. Burke (storageanarchy at gmail dot com)",
	description: "If a larger than configured delta is found between sensors the fan circulation time will be increased by 5 minutes every hour.",
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
        	input (name: "theThermostat", type:"capability.Thermostat", title: "Pick Ecobee Thermostat(s)", required: true, 
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
        	// Settings option for using Mode or Routine
            input(name: "theSensors", title: "Pick temperature sensor(s)", type: "capability.Temperature Measurement", 
				  required: true, multiple: true, submitOnChange: true)
		}
		
		section(title: "Max Temperature Delta" ){
            input(name: "deltaTemp", type: "decimal", title: "Enter temperature delta for making adjustments", required: true,
				  defaultValue: "2.0", description: "2.0", range "1..9")
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
    
    // create state variables:
    atomicState.lastAdjustment = now() - (3601 * settings.fanAdjustMinutes.toLong()).toLong()
	initialize()  
}

def updated() {
	LOG("updated() entered", 5)
	unsubscribe()
    
    //verify state variable
    initialize()
}

def initialize() {
	LOG("initialize() entered")

	// Now, just exit if we are disabled...
	if(tempDisable == true) {
    	LOG("Temporarily Disabled as per request.", 2, null, "warn")
    	return true
    }
	
    atomicState.lastAdjustment = null
    
    subscribe(theThermostats, "thermostatOperatingState", deltaHandler)
    subscribe(theSensors, "temperature", deltaHandler)
	subscribe(location, "mode", deltaHandler)

    LOG("initialize() complete", 3)
}

def deltaHandler(evt=null) {
    LOG("temperatureHandler() entered with evt: ${evt}", 5)
    
    if (atomicState.lastAdjustment) {
        def timeNow = now()
        if (timeNow <= (atomicState.lastAdjustment + (3600*settings.fanAdjustMinutes))) {
            LOG("Not time to adjust yet",3)
            return
		}
	}

	Double temps = theSensors?.currentValue("temperature").toDouble()
	
    2. Find the min, max & delta between all the subscribed thermometers
    3. if delta > settings.deltaTemp
    3a.     if current.fanMinOnTime.toInteger() < maxFanOnTime
    3b.         new fanMinOnTime += 5settings.fanTimeDelta minutes
    3c.         if (new.fanMinOnTime > settings.maxFanOnTime) 
    3d.             new.FanMinOnTime = settings.maxfanOnTime
    3e.         log the time of this change
    4. else if (delta < deltaTemp)
    4a.     if current.fanMinOnTime.toInteger() > minFanOnTime
    4b.         new fanMinOnTime -= settings.fanTimeDelta minutes
    4c.         if (new.fanMinOnTime < settings.minFanOnTime)
    4d              new.fanMinOnTime = settings.minFanOnTime
    4e.         log the time of this change
    4f. else // gap has been closed, stop adjusting
    
    */
}

// Helper Functions
private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	parent.LOG(message, level, child, logType, event, displayEvent)
}
