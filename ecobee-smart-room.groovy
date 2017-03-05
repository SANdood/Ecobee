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
	description: "Automates a 'room' with sensors (ecobee sensor, door, windows, occupancy), adding/removing the room from selected climates and (optionally) opening/closing SmartThings-controlled vents.",
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
        
        section(title: "Select ecobee Sensor(s)") {
        	if (settings.tempDisable == true) paragraph "WARNING: Temporarily Disabled as requested. Turn back on to activate handler."
            paragraph("A 'Smart Room' is defined by a specific collection of one or more Ecobee Sensors. Additional temperature, motion, door/windows contact sensors and automated vents can be selected separately below.")
        	input(name: "theSensors", type:"enum", title: "Use which Ecobee Sensor(s)", options: getEcobeeSensorsList(), required: true, multiple: true, submitOnChange: true)
			input(name: "activeProgs", type:"enum", title: "Include in which programs while Smart Room is enabled?", options: getProgramsList(), required: true, multiple: true)
			input(name: "inactiveProgs", type:"enum", title: "Include in which programs while Smart Room is disabled?", options: getProgramsList(),required: true, multiple: true)
		}
		
        
        section(title: "Smart Room Doors") {
            paragraph("Doors are used to enable or disable a Smart Room based on how long the door is left open or closed")
            input(name: "theDoors", title: "Which Door contact sensor(s)", type: "capability.contactSensor", required: true, multiple: true, submitOnChange: true)
            if (theDoors) {
            	paragraph("An open door signals to bring this Smart Room on-line. How long should the door be open before the room is enabled?")
            	input(name: "doorOpenMinutes", title: "Elapsed time before Smart Room is enabled (minutes)?", type: number, required: true, defaultValue: '5', description: '5', range: "5..60")
            	paragraph("A closed door signals to take this Smart Room off-line. How long should the door be closed before the room is disabled?")
            	input(name: "doorClosedHours", title: "Elapsed time before Smart Room is disabled (hours)?", type: number, required: true, defaultValue: '12', description: '12', range: "4..24")
            }
		}
        
       	section(title: "Smart Room Windows (Optional)") {
        	paragraph("Windows will temporarily disable s Smart Room")
            input(name: "theWindows", type: "capability.contactSensor", title: "Which Window contact sensor(s)", required: false, multiple:true)
            if (theWindows) {
            	input(name: "windowOpenMinutes", title: "Window open for how many minutes takes this room off-line?", type: number, required: false, range: "1..60")
            	input(name: "windowClosedMinutes", title: "Window closed for how many minutes brings this room back on-line?", type: number, required: false, range: "1..60")
            }
        }
       
        section(title: "Smart Room Vents") {
        	paragraph("Smart Rooms will automatically open specified vents while enabled, and close them when disabled")
            input(name: "theEconetVents", type: "device.econetVent", title: "Control which EcoNet Vent(s)", required: false, multiple:true)
            
        }
       
		section(title: "Smart Room - Additional Sensors") {
        }
        
        section(title: "Smart Room - Only Active during SmartThings Modes or Ecobee Programs") {
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

def getEcobeeSensorsList() {
	return parent.getEcobeeSensors().sort { it.value }
}

def initialize() {
	LOG("${getVersionLabel()}\nInitializing...", 3, "", 'info')
    
    atomicState.isSmartRoomActive = false
    def sensorData = [:]
	
    if (theDoors) {
    	subscribe(theDoors, "contact.open", doorOpenHandler)
    	subscribe(theDoors, "contact.closed", doorClosedHandler)
		log.debug "theDoors.getLastActivity(): ${theDoors.getLastActivity()}"
		sensorData += [ doors: ((theDoors.currentContact.contains('open')) ? 'open' : 'closed')	]
	} else {
		sensorData += [ doors: 'default' ]
	}
	
    if (theWindows) {
    	subscribe(theWindows, "contact.open", windowOpenHandler)
    	subscribe(theWindows, "contact.closed", windowClosedHandler)
		log.debug "theWindows.getLastActivity(): ${theWindows.getLastActivity()}"
		sensorData += [ windows: ((theWindows.currentContact.contains('open')) ? 'open' : 'closed')	]
	} else {
		sensorData += [ windows: 'default' ]
    }
	
	if (theEcobeeVents) {
		sensorData += [ vents: ((theEcobeeVents.currentSwitch.contains('on')) ? 'open' : 'closed')	]
	} else {
		sensorData += [ vents: 'default' ]
	}
	
/*    if (theDoors?.currentContact.contains('open')) {
    	// How long have we been open?
        if (openTime >= settings.doorOpenMinutes) { 
      		activateRoom()
        }
    } else {
    	// How long have we been closed?
        if (closedTime >= settings.doorCLosedHours) {
        	deactivateRoom()
        }
    }
*/
	// update the device handler status display
	theSensors.generateEvent(sensorData)
}

def doorOpenHandler(evt) {
		runIn(doorOpenMinutes*60, setRoomActivation, [data: [value:"active"], overwrite: true])
}

def doorClosedHandler(evt) {
		runIn(doorClosedHours*3600, setRoomActivation, [data: [value:"inactive"], overwrite: true])
}

def windowOpenHandler(evt) {
	deactivateRoom()
}

def windowClosedHandler(evt) {
	// activate Smart Room if ALL of the windows are closed
	if (!theWindows.currentContact().contains('open')) {
    	activateRoom()
    }
}

def setRoomActivation(desiredState) {
	if (desiredState == 'active') {
		if (atomicState.isSmartRoomActive) {
			LOG("Smart Room is already active",2,this,'warn')
		} else {
			activateRoom()
		}
	} else if (desiredState == 'inactive') {
		if (atomicState.isSmartRoomActive) {
			deactiveRoom()
		} else {
			LOG("Smart Room is already inactive",2,this,'warn')
		}
	} else {
		LOG("Error, invalid activation state ${desiredStat}",1,this,'warn')
	}
}
	
def activateRoom() {
	log.debug "activateRoom()"
}

def deactivateRoom() {
	log.debug "deactivateRoom()"
}

// Helper Functions
private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	message = "${app.label} ${message}"
	if (parent) parent.LOG(message, level, child, logType, event, displayEvent)
    if (level <= 4) log."${logType}" message
}
