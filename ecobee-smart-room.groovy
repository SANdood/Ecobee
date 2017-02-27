/**
 *  ecobee Smart Room
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
 *	0.1.0	02/27/2017	Barry Burke -	Initial Release
 */
def getVersionNum() { return "0.1.0" }
private def getVersionLabel() { return "ecobee Smart Room Version ${getVersionNum()}" }
import groovy.json.JsonSlurper

definition(
	name: "ecobee Smart Room",
	namespace: "smartthings",
	author: "Barry A. Burke (storageanarchy at gmail dot com)",
	description: "Automates a "room" ecobee sensor, door, windows, occupancy.",
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
	dynamicPage(name: "mainPage", title: "Configure Smart Room", uninstall: true, install: true) {
    	section(title: "Name for Smart Room Handler") {
        	label title: "Name this Smart Room Handler", required: true      
        }
        
        section(title: "Select ecobee Sensor") {
        	if(settings.tempDisable == true) paragraph "WARNING: Temporarily Disabled as requested. Turn back on to activate handler."
            paragraph("A 'Smart Room' is defined by a specific ecobee Sensor. Additional temperature and motion sensors can be selected separately below.")
        	input(name: "theSensor", type:"enum", title: "Use which Ecobee Sensor", options: getSensorsList(), required: true, multiple: false, submitOnChange: true)            
		}
                
        section(title: "Smart Room Doors") {
            paragraph("'Doors' are used by a 'Smart Room' to turn a room on or off."
            input(name: "theDoors", title: "Use which Door contact sensor(s)", type: "capability.contactSensor", required: true, multiple: true, submitOnChange: true)
            paragaph("An open door signals to bring this Smart Room on-line. How long should the door be open before the room is considered on-line?")
            input(name: "doorOpenMinutes", title: "Door open for how many minutes brings this Smart Room on-line?", type: number, required: true, defaultValue: '5', description: '5', range: "5..60")
            paragraph("A closed door signals to take this Smart Room off-line. How long should the door be closed before the room goes off-line?")
            input(name: "doorClosedHours", title: "Door closed for how many hours before this Smart Room is off-line?", type: number, required: true, defaultValue: '12', description: '12', range: "4..24")          
		}
        
       	section(title: "Smart Room Windows (Optional)") {
        	paragraph("Windows will take a Smart Room temporarily off-line if they are open.")
            input(name: "theWindows", type: "capability.contactSensor", title: "Select windows", required: false, multiple:true)
            input(name: "windowOpenMinutes", title: "Window open for how many minutes takes this room off-line?", type: number, required: false, range: "1..60")
            input(name: "windowClosedMinutes", title: "Window closed for how many minutes brings this room back on-line?", type: number, required: false, range: "1..60")
        }
       
        section(title: "Smart Room Vents") {
        	paragraph("Smart Rooms will automatically open vents when on-line, and close them when off-line")
            input(name: "theVents", type: "ADJUSTABLE VENTS", title: "Control which vent(s)", required: true, multiple:true)
        }
       
		section(title: "Smart Room - Additional Sensors") {
        	paragraph("Circulation time (min/hr) is only adjusted while in these modes *OR* programs. The time will remain at the last setting while in other modes. If you want different circulation times for other modes or programs, create multiple Smart Circulation handlers.")
            input(name: "theModes",type: "mode", title: "Only when the Location Mode is", multiple: true, required: false)
            input(name: "thePrograms", type: "enum", title: "Only when the ${theThermostat ? theThermostat : 'thermostat'}'s Program is", multiple: true, required: false, options: getProgramsList())
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
}

def updated() {
	LOG("updated() entered", 4, "", 'trace')
	unsubscribe()
    unschedule()
    initialize()
}

def getProgramsList() {
    return theThermostat ? new JsonSlurper().parseText(theThermostat.currentValue('programsList')) : ["Away","Home","Sleep"]
}

def initialize() {
	LOG("${getVersionLabel()}\nInitializing...", 3, "", 'info')
}

// Helper Functions
private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	parent.LOG(message, level, child, logType, event, displayEvent)
    if (level <= 4) log.info message
}
