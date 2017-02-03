/**
 *  Ecobee Sensor
 *
 *  Copyright 2015 Juan Risso
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
 *  See Changelog for change history
 *	0.10.1 - Tweaks to display decimal precision
 *	0.10.2 - Fixed so that Temperature shows in large font for Room/Thing List vies
 *
 */

def getVersionNum() { return "0.10.2" }
private def getVersionLabel() { return "Ecobee Sensor Version ${getVersionNum()}-RC8" }

metadata {
	definition (name: "Ecobee Sensor", namespace: "smartthings", author: "SmartThings") {
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Motion Sensor"
		capability "Refresh"
		capability "Polling"
		
		attribute "decimalPrecision", "number"
		attribute "temperatureDisplay", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"temperatureDisplay", type: "generic", width: 6, height: 4){
			tileAttribute ("device.temperatureDisplay", key: "PRIMARY_CONTROL") {
				attributeState("temperature", label:'${currentValue}',
					backgroundColors: getTempColors())
			}
			tileAttribute ("device.motion", key: "SECONDARY_CONTROL") {
                attributeState "active", action:"noOp", nextState: "active", label:"Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_motion.png"
				attributeState "inactive", action: "noOp", nextState: "inactive", label:"No Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_nomotion.png"
            	attributeState "unkown", action: "noOp", label:"Offline", nextState: "unkown", icon: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_noconnection.png"
           	 	attributeState "not supported", action: "noOp", nextState: "not supported", label: "N/A", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/notsupported_x.png"
            }
		}
        
		valueTile("temperature", "device.temperature", width: 2, height: 2, canChangeIcon: false, icon: "st.Home.home1") {
            state("temperature", defaultState: true, label:'${currentValue}°', unit:"F",
				backgroundColors: getTempColors()
			)
		}
        
        standardTile("motion", "device.motion", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "active", action:"noOp", nextState: "active", label:"Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_motion.png"
			state "inactive", action: "noOp", nextState: "inactive", label:"No Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_nomotion.png"
            state "unkown", action: "noOp", label:"Offline", nextState: "unkown", icon: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_noconnection.png"
            state "not supported", action: "noOp", nextState: "not supported", label: "N/A", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/notsupported_x.png"
		}
/*
	standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
*/
		standardTile("refresh", "device.thermostatMode", width: 2, height: 2,inactiveLabel: false, decoration: "flat") {
            state "default", action:"refresh.refresh", label: "Refresh", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/header_ecobeeicon_blk.png"
		}


		main (["temperature", "temperatureDisplay",])
		details(["temperatureDisplay","refresh"])
	}
}

def refresh() {
	LOG( "Ecobee Sensor: refresh()...", 4, this, "trace")
	poll()
}

void poll() {
	LOG( "Ecobee Sensor: Executing 'poll' using parent SmartApp", 4, this, "trace")
	parent.pollChildren(this)
}


def generateEvent(Map results) {
	def tempScale = getTemperatureScale()
	log.debug "generateEvent(): parsing data $results. F or C? ${tempScale}"
	if(results) {
		String tempDisplay = ""
		results.each { name, value ->			
			def linkText = getLinkText(device)
			def isChange = false
			def isDisplayed = true
			def event = [:]  // [name: name, linkText: linkText, handlerName: name]
           
			if (name=="temperature") {
				def sendValue = value
                
                if (sendValue == "unknown") {
                	// We are OFFLINE
                    LOG( "Warning: Remote Sensor (${name}) is OFFLINE. Please check the batteries or move closer to the thermostat.", 2, null, "warn")
                    state.onlineState = false
                    sendValue = "unknown"
                } else {
                	// must be online
                    state.onlineState = true   
					isChange = isStateChange(device, name, "${sendValue}")
                    
                    // Generate the display value that will preserve decimal positions ending in 0
                    if (isChange) {
						def precision = device.currentValue("decimalPrecision")
                    	if (!precision) precision = (tempScale == "C") ? 1 : 0
                    	if (precision == 0) {
                    		tempDisplay = value.toDouble().round(0).toInteger().toString() + '°'
                    	} else {
							tempDisplay = String.format( "%.${precision.toInteger()}f", value.toDouble().round(precision.toInteger())) + '°'
                    	}
                    }
                }
				
				// isDisplayed = isChange
				if (isChange) event = [name: name, linkText: linkText, desciptionText: "Temperature is ${tempDisplay}°", handlerName: name, value: sendValue, isStateChange: true, displayed: true]
				
			} else if (name=="motion") {        
            	def sendValue = value
            
                if ( (sendValue == "unknown") || (!state.onlineState) ) {
                	// We are OFFLINE
                    LOG( "Warning: Remote Sensor (${name}) is OFFLINE. Please check the batteries or move closer to the thermostat.", 2, null, "warn")
                    sendValue = "unknown"
                }
                
				isChange = isStateChange(device, name, sendValue.toString())
				// isDisplayed = isChange
				if (isChange) event = [name: name, linkText: linkText, descriptionText: "Motion is ${sendValue}", handlerName: name, value: sendValue.toString(), isStateChange: true, displayed: true]
			} else {
				isChange = isStateChange(device, name, value.toString())
				// isDisplayed = isChange
				if (isChange) event = [name: name, linkText: linkText, handlerName: name, value: value.toString(), isStateChange: true, displayed: true]
            }
			if (event != [:]) sendEvent(event)
		}
		if (tempDisplay) {
			sendEvent( name: "temperatureDisplay", linkText: linkText, value: "${tempDisplay}", handlerName: "temperatureDisplay", descriptionText: "Display temperature is ${tempDisplay}", isStateChange: true, displayed: false)
		}
	}
}

//generate custom mobile activity feeds event
def generateActivityFeedsEvent(notificationMessage) {
	sendEvent(name: "notificationMessage", value: "$device.displayName $notificationMessage", descriptionText: "$device.displayName $notificationMessage", displayed: true)
}

private debugLevel(level=3) {
	def debugLvlNum = parent.settings.debugLevel?.toInteger() ?: 3
    def wantedLvl = level?.toInteger()
    
    return ( debugLvlNum >= wantedLvl )
}

private def LOG(message, level=3, child=null, logType="debug", event=false, displayEvent=false) {
	def prefix = ""
	if ( parent.settings.debugLevel?.toInteger() == 5 ) { prefix = "LOG: " }
	if ( debugLevel(level) ) { 
    	log."${logType}" "${prefix}${message}"
        // log.debug message
        if (event) { debugEvent(message, displayEvent) }        
	}    
}

private def debugEvent(message, displayEvent = false) {

	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	if ( debugLevel(4) ) { log.debug "Generating AppDebug Event: ${results}" }
	sendEvent (results)
}
	
def getTempColors() {
	def colorMap

	colorMap = [
		// Celsius Color Range
		[value: 0, color: "#1e9cbb"],
		[value: 15, color: "#1e9cbb"],
		[value: 19, color: "#1e9cbb"],

		[value: 21, color: "#44b621"],
		[value: 22, color: "#44b621"],
		[value: 24, color: "#44b621"],

		[value: 21, color: "#d04e00"],
		[value: 35, color: "#d04e00"],
		[value: 37, color: "#d04e00"],
		// Fahrenheit Color Range
		[value: 40, color: "#1e9cbb"],
		[value: 59, color: "#1e9cbb"],
		[value: 67, color: "#1e9cbb"],

		[value: 69, color: "#44b621"],
		[value: 72, color: "#44b621"],
		[value: 74, color: "#44b621"],

		[value: 76, color: "#d04e00"],
		[value: 95, color: "#d04e00"],
		[value: 99, color: "#d04e00"],
        
        [value: 451, color: "#ffa81e"] // Nod to the book and temp that paper burns. Used to catch when the device is offline
	]
}

def getStockTempColors() {
	def colorMap
    
    colorMap = [
    	[value: 32, color: "#153591"],
        [value: 44, color: "#1e9cbb"],
        [value: 59, color: "#90d2a7"],
        [value: 74, color: "#44b621"],
        [value: 84, color: "#f1d801"],
        [value: 92, color: "#d04e00"],
        [value: 98, color: "#bc2323"]
    ]       
}
