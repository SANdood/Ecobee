/**
 *  Based on original code Copyright 2015 SmartThings
 *	Additional changes Copyright 2016 Sean Kendall Schneyer
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
 *	Ecobee Service Manager
 *
 *	Author: scott
 *	Date: 2013-08-07
 *
 *  Last Modification:
 *      JLH - 01-23-2014 - Update for Correct SmartApp URL Format
 *      JLH - 02-15-2014 - Fuller use of ecobee API
 *      10-28-2015 DVCSMP-604 - accessory sensor, DVCSMP-1174, DVCSMP-1111 - not respond to routines
 *      StrykerSKS - 12-11-2015 - Make it work (better) with the Ecobee 3
 *
 *  See Changelog for change history
 *
 * 	0.9.18 - Fix customer Program handling
 *	0.9.19 - Add attributes to indicate custom program names to child thermostats (smart1, smart2, etc)
 *  0.9.20 - Allow installations where no "location" is set. Useful for virtual hubs and testing
 *
 */  
def getVersionNum() { return "0.9.20" }
private def getVersionLabel() { return "Ecobee (Connect) Version ${getVersionNum()}" }
private def getHelperSmartApps() {
	return [ 
    		[name: "ecobeeRoutinesChild", appName: "ecobee Routines",  
            	namespace: "smartthings", multiple: true, 
                title: "Create new Routines Handler..."], 
			[name: "ecobeeContactsChild", appName: "ecobee Open Contacts",  
            	namespace: "smartthings", multiple: true, 
                title: "Create new Open Contacts SmartApp..."]                 
			]
}
 
definition(
	name: "Ecobee (Connect)",
	namespace: "smartthings",
	author: "Sean Kendall Schneyer",
	description: "Connect your Ecobee thermostat to SmartThings.",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: true
) {
	appSetting "clientId"
}

preferences {
	page(name: "mainPage")
    page(name: "removePage")
	page(name: "authPage")
	page(name: "thermsPage")
	page(name: "sensorsPage")
    page(name: "preferencesPage")    
    page(name: "addWatchdogDevicesPage")
    page(name: "helperSmartAppsPage")    
    // Part of debug Dashboard
    page(name: "debugDashboardPage")
    page(name: "pollChildrenPage")
    page(name: "updatedPage")
    page(name: "refreshAuthTokenPage")    
}

mappings {
	path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
	path("/oauth/callback") {action: [GET: "callback"]}
}


// Begin Preference Pages
def mainPage() {	
	def deviceHandlersInstalled 
    def readyToInstall 
    
    // Only create the dummy devices if we aren't initialized yet
    if (!atomicState.initialized) {
    	deviceHandlersInstalled = testForDeviceHandlers()
    	readyToInstall = deviceHandlersInstalled
	}
    if (atomicState.initialized) { readyToInstall = true }
    
	dynamicPage(name: "mainPage", title: "Welcome to ecobee (Connect)", install: readyToInstall, uninstall: false, submitOnChange: true) {
    	def ecoAuthDesc = (atomicState.authToken != null) ? "[Connected]\n" :"[Not Connected]\n"        
		
        // If not device Handlers we cannot proceed
        if(!atomicState.initialized && !deviceHandlersInstalled) {
			section() {
				paragraph "ERROR!\n\nYou MUST add the ${getChildThermostatName()} and ${getChildSensorName()} Device Handlers to the IDE BEFORE running the setup."				
			}		
        } else {
        	readyToInstall = true
        }
        
        if(atomicState.initialized && !atomicState.authToken) {
        	section() {
				paragraph "WARNING!\n\nYou are no longer connected to the ecobee API. Please re-Authorize below."				
			}
        }       

		if(atomicState.authToken != null && atomicState.initialized != true) {
        	section() {
            	paragraph "Please click 'Done' to save your credentials. Then re-open the SmartApp to continue the setup."
            }
        }

		// Need to save the initial login to setup the device without timeouts
		if(atomicState.authToken != null && atomicState.initialized) {
        	if (settings.thermostats?.size() > 0 && atomicState.initialized) {
            	section("Helper SmartApps") {
                	href ("helperSmartAppsPage", title: "Helper SmartApps", description: "Tap to manage Helper SmartApps")
                }            
            }
			section("Devices") {
				def howManyThermsSel = settings.thermostats?.size() ?: 0
                def howManyTherms = atomicState.numAvailTherms ?: "?"
                def howManySensors = atomicState.numAvailSensors ?: "?"
                
                // Thermostats
				atomicState.settingsCurrentTherms = settings.thermostats ?: []
    	    	href ("thermsPage", title: "Thermostats", description: "Tap to select Thermostats [${howManyThermsSel}/${howManyTherms}]")                
                
                // Sensors
            	if (settings.thermostats?.size() > 0) {
					atomicState.settingsCurrentSensors = settings.ecobeesensors ?: []
                	def howManySensorsSel = settings.ecobeesensors?.size() ?: 0
                    if (howManySensorsSel > howManySensors) { howManySensorsSel = howManySensors } // This is due to the fact that you can remove alread selected hiden items
            		href ("sensorsPage", title: "Sensors", description: "Tap to select Sensors [${howManySensorsSel}/${howManySensors}]")
	            }
    	    }        
	        section("Preferences") {
    	    	href ("preferencesPage", title: "Preferences", description: "Tap to review SmartApp settings.")
                LOG("In Preferences page section after preferences line", 5, null, "trace")
        	}
            if( settings.useWatchdogDevices == true ) {
            	section("Extra Poll and Watchdog Devices") {
                	href ("addWatchdogDevicesPage", title: "Watchdog Devices", description: "Tap to select Poll and Watchdog Devices.")
                }
            }
           
    	} // End if(atomicState.authToken)
        
        // Setup our API Tokens       
		section("Ecobee Authentication") {
			href ("authPage", title: "ecobee Authorization", description: "${ecoAuthDesc}Tap for ecobee Credentials")
		}      
		if ( debugLevel(5) ) {
			section ("Debug Dashboard") {
				href ("debugDashboardPage", description: "Tap to enter the Debug Dashboard", title: "Debug Dashboard")
			}
		}
		section("Remove ecobee (Connect)") {
			href ("removePage", description: "Tap to remove ecobee (Connect) ", title: "Remove ecobee (Connect)")
		}            
		
		section ("Name this instance of ecobee (Connect)") {
			label name: "name", title: "Assign a name", required: true, defaultValue: app.name
		}
     
		section (getVersionLabel())
	}
}


def removePage() {
	dynamicPage(name: "removePage", title: "Remove ecobee (Connect) and All Devices", install: false, uninstall: true) {
    	section ("WARNING!\n\nRemoving ecobee (Connect) also removes all Devices\n") {
        }
    }
}

// Setup OAuth between SmartThings and Ecobee clouds
def authPage() {
	LOG("=====> authPage() Entered", 5)

	if(!atomicState.accessToken) { //this is an access token for the 3rd party to make a call to the connect app
		atomicState.accessToken = createAccessToken()
	}

	def description = "Click to enter ecobee Credentials"
	def uninstallAllowed = false
	def oauthTokenProvided = false

	if(atomicState.authToken) {
		description = "You are connected. Tap Done above."
		uninstallAllowed = true
		oauthTokenProvided = true
        apiRestored()
	} else {
		description = "Tap to enter ecobee Credentials"
	}

	def redirectUrl = buildRedirectUrl //"${serverUrl}/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}"
    LOG("authPage() --> RedirectUrl = ${redirectUrl}")
    
	// get rid of next button until the user is actually auth'd
	if (!oauthTokenProvided) {
    	LOG("authPage() --> in !oauthTokenProvided")    	
		return dynamicPage(name: "authPage", title: "ecobee Setup", nextPage: "", uninstall: uninstallAllowed) {
			section() {
				paragraph "Tap below to log in to the ecobee service and authorize SmartThings access. Be sure to press the 'Allow' button on the 2nd page."
				href url:redirectUrl, style:"embedded", required:true, title: "ecobee Account Authorization", description:description 
			}
		}
	} else {    	
        LOG("authPage() --> in else for oauthTokenProvided - ${atomicState.authToken}.")
        return dynamicPage(name: "authPage", title: "ecobee Setup", nextPage: "mainPage", uninstall: uninstallAllowed) {
        	section() {
            	paragraph "Return to main menu."
                href url:redirectUrl, style: "embedded", state: "complete", title: "ecobee Account Authorization", description: description
			}
        }           
	}
}

// Select which Thermostats are to be used
def thermsPage(params) {
	LOG("=====> thermsPage() entered", 5)        
	def stats = getEcobeeThermostats()
    LOG("thermsPage() -> thermostat list: ${stats}")
    LOG("thermsPage() starting settings: ${settings}")
    LOG("thermsPage() params passed? ${params}", 4, null, "trace")

    dynamicPage(name: "thermsPage", title: "Select Thermostats", params: params, nextPage: "", content: "thermsPage", uninstall: false) {    
    	section("Units") {
        	paragraph "NOTE: The units type (F or C) is determined by your Hub Location settings automatically. Please update your Hub settings (under My Locations) to change the units used. Current value is ${getTemperatureScale()}."
        }
    	section("Select Thermostats") {
			LOG("thersPage(): atomicState.settingsCurrentTherms=${atomicState.settingsCurrentTherms}   settings.thermostats=${settings.thermostats}", 4, null, "trace")
			if (atomicState.settingsCurrentTherms != settings.thermostats) {
				LOG("atomicState.settingsCurrentTherms != settings.thermostats determined!!!", 4, null, "trace")			
			} else { LOG("atomicState.settingsCurrentTherms == settings.thermostats: No changes detected!", 4, null, "trace") }
        	paragraph "Tap below to see the list of ecobee thermostats available in your ecobee account and select the ones you want to connect to SmartThings."
			input(name: "thermostats", title:"Select Thermostats", type: "enum", required:false, multiple:true, description: "Tap to choose", params: params, metadata:[values:stats], submitOnChange: true)        
        }      
    }      
}

def sensorsPage() {
	// Only show sensors that are part of the chosen thermostat(s)
    // Refactor to show the sensors under their corresponding Thermostats. Use Thermostat name as section header?
    LOG("=====> sensorsPage() entered. settings: ${settings}", 5)
    atomicState.sensorsPageVisited = true

	def options = getEcobeeSensors() ?: []
	def numFound = options.size() ?: 0
    
    LOG("options = getEcobeeSensors == ${options}")

    dynamicPage(name: "sensorsPage", title: "Select Sensors", nextPage: "") {
		if (numFound > 0)  {
			section("Select Sensors"){
				LOG("sensorsPage(): atomicState.settingsCurrentSensors=${atomicState.settingsCurrentSensors}   settings.ecobeesensors=${settings.ecobeesensors}", 4, null, "trace")
				if (atomicState.settingsCurrentSensors != settings.ecobeesensors) {
					LOG("atomicState.settingsCurrentSensors != settings.ecobeesensors determined!!!", 4, null, "trace")					
				} else { LOG("atomicState.settingsCurrentSensors == settings.ecobeesensors: No changes detected!", 4, null, "trace") }
				paragraph "Tap below to see the list of ecobee sensors available for the selected thermostat(s) and select the ones you want to connect to SmartThings."
                if (settings.showThermsAsSensor) { paragraph "NOTE: Also showing Thermostats as an available sensor to allow for actual temperature values to be used." }
				input(name: "ecobeesensors", title:"Select Ecobee Sensors (${numFound} found)", type: "enum", required:false, description: "Tap to choose", multiple:true, metadata:[values:options])
			}
		} else {
    		 // No sensors associated with this set of Thermostats was found
           LOG("sensorsPage(): No sensors found.", 4)
           section(""){
           		paragraph "No associated sensors were found. Click Done above."
           }
	    }        
	}
}

def preferencesPage() {
    LOG("=====> preferencesPage() entered. settings: ${settings}", 5)

    dynamicPage(name: "preferencesPage", title: "Update SmartApp Preferences", nextPage: "") {
		section("SmartApp Preferences") {
        	input(name: 'recipients', title: 'Send push notifications to', type: 'contact', required: false, multiple: true)
        	input(name: "holdType", title:"Select Hold Type", type: "enum", required:false, multiple:false, defaultValue:  "Until I Change", description: "Until I Change", metadata:[values:["Until I Change", "Until Next Program"]])
            paragraph "The 'Smart Auto Temperature Adjust' feature determines if you want to allow the thermostat setpoint to be changed using the arrow buttons in the Tile when the thermostat is in 'auto' mode."
            input(name: "smartAuto", title:"Use Smart Auto Temperature Adjust?", type: "bool", required:false, defaultValue: false, description: "")
            input(name: "pollingInterval", title:"Polling Interval (in Minutes)", type: "enum", required:false, multiple:false, defaultValue:5, description: "5", options:["1", "2", "3", "5", "10", "15", "30"])
            input(name: "debugLevel", title:"Debugging Level (higher # for more information)", type: "enum", required:false, multiple:false, defaultValue:3, description: "3", metadata:[values:["5", "4", "3", "2", "1", "0"]])            
            paragraph "Showing a Thermostat as a separate Sensor is useful if you need to access the actual temperature in the room where the Thermostat is located and not just the (average) temperature displayed on the Thermostat"
            input(name: "showThermsAsSensor", title:"Include Thermostats as a separate Ecobee Sensor?", type: "bool", required:false, defaultValue: false, description: "")
            paragraph "Monitoring external devices can be used to drive polling and the watchdog events. Be warned, however, not to select too many devices or devices that will send too many events as this can cause issues with the connection."
            input(name: "useWatchdogDevices", title:"Monitor external devices to drive additional polling and watchdog events?", type: "bool", required:false, description: "", defaultValue:false)
            paragraph "Set the pause between pressing the setpoint arrows and initiating the API calls. The pause needs to be long enough to allow you to click the arrow again for changing by more than one degree."
            input(name: "arrowPause", title:"Delay timer value after pressing setpoint arrows", type: "enum", required:false, multiple:false, description: "4", defaultValue:5, options:["1", "2", "3", "4", "5"])
			paragraph "Set the desired number of decimal places to display for all temperatures (recommended 1 for C, 0 for F)."
			String digits = wantMetric() ? "1" : "0"
			input(name: "tempDecimals", title:"Decimal places to display", type: "enum", required:false, multiple:false, defaultValue:digits, description: digits, metadata:[values:["0", "1", "2"]])
        }
	}
}

def addWatchdogDevicesPage() {
	LOG("Displaying the Watchdog Device Selection page next...", 5, null, "trace")
    dynamicPage(name: "addWatchdogDevicesPage", title: "Select Watchdog Devices", nextPage: "") {
		section("Polling and Watchdog Devices") {
        	paragraph ("Select device(s) that you wish to subscribe to in order to create additional polling events and trigger the watchdog timers. " +
            	"Do NOT select too many devices or devices that will cause excess polling. " + 
                "Ecobee only updates their data every 3 minutes so any polling interval greater than that is unnecessary.")
     		input(name: "watchdogMotion", type:"capability.motionSensor", title: "Select Motion Sensor(s)", required:false, multiple:true)
            input(name: "watchdogTemp", type:"capability.temperatureMeasurement", title: "Select Temperature Measurement Device(s)", required:false, multiple:true)
            input(name: "watchdogSwitch", type:"capability.switch", title: "Select Switch(es)", required:false, multiple:true)
            input(name: "watchdogBattery", type:"capability.battery", title: "Select Battery(ies)", required:false, multiple:true)
            input(name: "watchdogHumidity", type:"capability.relativeHumidityMeasurement", title: "Select Humidity Sensor(s)", required:false, multiple:true)
            input(name: "watchdogLuminance", type:"capability.illuminanceMeasurement", title: "Select Illuminance Sensor(s)", required:false, multiple:true)
        }
 	}
}

def debugDashboardPage() {
	LOG("=====> debugDashboardPage() entered.", 5)    
    
    dynamicPage(name: "debugDashboardPage", title: "") {
    	section (getVersionLabel())
		section("Commands") {
        	href(name: "pollChildrenPage", title: "", required: false, page: "pollChildrenPage", description: "Tap to execute: pollChildren()")
            href(name: "refreshAuthTokenPage", title: "", required: false, page: "refreshAuthTokenPage", description: "Tap to execute: refreshAuthToken()")
            href(name: "updatedPage", title: "", required: false, page: "updatedPage", description: "Tap to execute: updated()")
        }    	
        
    	section("Settings Information") {
        	paragraph "debugLevel: ${settings.debugLevel} (default=3 if null)"
            paragraph "holdType: ${settings.holdType} (default='Until I Change' if null)"
            paragraph "pollingInterval: ${settings.pollingInterval} (default=5 if null)"
            paragraph "showThermsAsSensor: ${settings.showThermsAsSensor} (default=false if null)"
            paragraph "smartAuto: ${settings.smartAuto} (default=false if null)"   
            paragraph "Selected Thermostats: ${settings.thermostats}"
        }
        section("Dump of Debug Variables") {
        	def debugParamList = getDebugDump()
            LOG("debugParamList: ${debugParamList}", 4, null, "debug")
            //if ( debugParamList?.size() > 0 ) {
			if ( debugParamList != null ) {
            	debugParamList.each { key, value ->  
                	LOG("Adding paragraph: key:${key}  value:${value}", 5, null, "trace")
                	paragraph "${key}: ${value}"
                }
            }
        }
    	section("Commands") {
        	href(name: "pollChildrenPage", title: "", required: false, page: "pollChildrenPage", description: "Tap to execute command: pollChildren()")
            href ("removePage", description: "Tap to remove ecobee (Connect) ", title: "")
        }
    }    
}

// pages that are part of Debug Dashboard
def pollChildrenPage() {
	LOG("=====> pollChildrenPage() entered.", 5)
    atomicState.forcePoll = true // Reset to force the poll to happen
	pollChildren(null)
    
	dynamicPage(name: "pollChildrenPage", title: "") {
    	section() {
        	paragraph "pollChildren() was called"
        }
    }    
}

// pages that are part of Debug Dashboard
def updatedPage() {
	LOG("=====> updatedPage() entered.", 5)
    updated()
    
	dynamicPage(name: "updatedPage", title: "") {
    	section() {
        	paragraph "updated() was called"
        }
    }    
}

def refreshAuthTokenPage() {
	LOG("=====> refreshAuthTokenPage() entered.", 5)
    refreshAuthToken()
    
	dynamicPage(name: "refreshAuthTokenPage", title: "") {
    	section() {
        	paragraph "refreshAuthTokenPage() was called"
        }
    }    
}

def helperSmartAppsPage() {
	LOG("helperSmartAppsPage() entered", 5)

	LOG("SmartApps available are ${getHelperSmartApps()}", 5, null, "info")
	
    //getHelperSmartApps() {
 	dynamicPage(name: "helperSmartAppsPage", title: "Helper Smart Apps", install: true, uninstall: false, submitOnChange: true) {    	
		getHelperSmartApps().each { oneApp ->
			LOG("Processing the app: ${oneApp}", 4, null, "trace")            
            def allowMultiple = oneApp.multiple.value
			section ("${oneApp.appName.value}") {
            	app(name:"${oneApp.name.value}", appName:"${oneApp.appName.value}", namespace:"${oneApp.namespace.value}", title:"${oneApp.title.value}", multiple: allowMultiple)            
			}
		}
	}
}
// End Prefernce Pages

// Preference Pages Helpers
private def Boolean testForDeviceHandlers() {
	if (atomicState.runTestOnce != null) { return atomicState.runTestOnce }
    
    def DNIAdder = now().toString()
    def d1
    def d2
    def success = true
    
	try {    	
		d1 = addChildDevice(app.namespace, getChildThermostatName(), "dummyThermDNI-${DNIAdder}", null, ["label":"Ecobee Thermostat:TestingForInstall", completedSetup:true])
		d2 = addChildDevice(app.namespace, getChildSensorName(), "dummySensorDNI-${DNIAdder}", null, ["label":"Ecobee Sensor:TestingForInstall", completedSetup:true])
	} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
		LOG("You MUST add the ${getChildThermostatName()} and ${getChildSensorName()} Device Handlers to the IDE BEFORE running the setup.", 1, null, "error")
		success = false
	}
    
    atomicState.runTestOnce = success
    
    if (d1) deleteChildDevice("dummyThermDNI-${DNIAdder}") 
    if (d2) deleteChildDevice("dummySensorDNI-${DNIAdder}") 
    
    return success
}
// End Preference Pages Helpers

// OAuth Init URL
def oauthInitUrl() {
	LOG("oauthInitUrl with callback: ${callbackUrl}", 5)
	atomicState.oauthInitState = UUID.randomUUID().toString()

	def oauthParams = [
			response_type: "code",
			client_id: smartThingsClientId,			
			scope: "smartRead,smartWrite",
			redirect_uri: callbackUrl, //"https://graph.api.smartthings.com/oauth/callback"
			state: atomicState.oauthInitState			
	]

	LOG("oauthInitUrl - Before redirect: location: ${apiEndpoint}/authorize?${toQueryString(oauthParams)}", 4)
	redirect(location: "${apiEndpoint}/authorize?${toQueryString(oauthParams)}")
}

// OAuth Callback URL and helpers
def callback() {
	LOG("callback()>> params: $params, params.code ${params.code}, params.state ${params.state}, atomicState.oauthInitState ${atomicState.oauthInitState}", 4)
	def code = params.code
	def oauthState = params.state

	//verify oauthState == atomicState.oauthInitState, so the callback corresponds to the authentication request
	if (oauthState == atomicState.oauthInitState){
    	LOG("callback() --> States matched!", 4)
		def tokenParams = [
			grant_type: "authorization_code",
			code      : code,
			client_id : smartThingsClientId,
			state	  : oauthState,
			redirect_uri: callbackUrl //"https://graph.api.smartthings.com/oauth/callback"
		]

		def tokenUrl = "${apiEndpoint}/token?${toQueryString(tokenParams)}"
        LOG("callback()-->tokenURL: ${tokenUrl}", 2)

		httpPost(uri: tokenUrl) { resp ->
			atomicState.refreshToken = resp.data.refresh_token
			atomicState.authToken = resp.data.access_token
            
            LOG("Expires in ${resp?.data?.expires_in} seconds")
            atomicState.authTokenExpires = now() + (resp.data.expires_in * 1000)
            LOG("swapped token: $resp.data; atomicState.refreshToken: ${atomicState.refreshToken}; atomicState.authToken: ${atomicState.authToken}", 2)
		}

		if (atomicState.authToken) { success() } else { fail() }

	} else {
    	LOG("callback() failed oauthState != atomicState.oauthInitState", 1, null, "warn")
	}

}

def success() {
	def message = """
    <p>Your ecobee Account is now connected to SmartThings!</p>
    <p>Click 'Done' to finish setup.</p>
    """
	connectionStatus(message)
}

def fail() {
	def message = """
        <p>The connection could not be established!</p>
        <p>Click 'Done' to return to the menu.</p>
    """
	connectionStatus(message)
}

def connectionStatus(message, redirectUrl = null) {
	def redirectHtml = ""
	if (redirectUrl) {
		redirectHtml = """
			<meta http-equiv="refresh" content="3; url=${redirectUrl}" />
		"""
	}

	def html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Ecobee & SmartThings connection</title>
<style type="text/css">
        @font-face {
                font-family: 'Swiss 721 W01 Thin';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
                font-weight: normal;
                font-style: normal;
        }
        @font-face {
                font-family: 'Swiss 721 W01 Light';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
                font-weight: normal;
                font-style: normal;
        }
        .container {
                width: 90%;
                padding: 4%;
                /*background: #eee;*/
                text-align: center;
        }
        img {
                vertical-align: middle;
        }
        p {
                font-size: 2.2em;
                font-family: 'Swiss 721 W01 Thin';
                text-align: center;
                color: #666666;
                padding: 0 40px;
                margin-bottom: 0;
        }
        span {
                font-family: 'Swiss 721 W01 Light';
        }
</style>
</head>
<body>
        <div class="container">
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/ecobee%402x.png" alt="ecobee icon" />
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" />
                ${message}
        </div>
</body>
</html>
"""

	render contentType: 'text/html', data: html
}
// End OAuth Callback URL and helpers

// Get the list of Ecobee Thermostats for use in the settings pages
def getEcobeeThermostats() {	
	LOG("====> getEcobeeThermostats() entered", 5)    
 	def requestBody = '{"selection":{"selectionType":"registered","selectionMatch":"","includeRuntime":true,"includeSensors":true,"includeProgram":true}}'
	def deviceListParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.authToken}"],
			query: [format: 'json', body: requestBody]
	]

	def stats = [:]
    try {
        httpGet(deviceListParams) { resp ->
		LOG("getEcobeeThermostats() - httpGet() response: ${resp.data}", 4)
        
        // Initialize the Thermostat Data. Will reuse for the Sensor List intialization
        atomicState.thermostatData = resp.data        	
        
            if (resp.status == 200) {
            	LOG("getEcobeeThermostats() - httpGet() in 200 Response", 3)
                atomicState.numAvailTherms = resp.data.thermostatList?.size() ?: 0
                
            	resp.data.thermostatList.each { stat ->
					def dni = [app.id, stat.identifier].join('.')
					stats[dni] = getThermostatDisplayName(stat)
                }
            } else {                
                LOG("getEcobeeThermostats() - httpGet() in else: http status: ${resp.status}", 1)
                //refresh the auth token
                if (resp.status == 500 && resp.data.status.code == 14) {
                	LOG("getEcobeeThermostats() - Storing the failed action to try later", 1)
                    atomicState.action = "getEcobeeThermostats"
                    LOG("getEcobeeThermostats() - Refreshing your auth_token!", 1)
                    refreshAuthToken()
                } else {
                	LOG("getEcobeeThermostats() - Other error. Status: ${resp.status}  Response data: ${resp.data} ", 1)
                }
            }
        }
    } catch(Exception e) {
    	LOG("___exception getEcobeeThermostats(): ${e}", 1, null, "error")
        refreshAuthToken()
    }
	atomicState.thermostatsWithNames = stats
    LOG("atomicState.thermostatsWithNames == ${atomicState.thermostatsWithNames}", 4)
	return stats
}

// Get the list of Ecobee Sensors for use in the settings pages (Only include the sensors that are tied to a thermostat that was selected)
// NOTE: getEcobeeThermostats() should be called prior to getEcobeeSensors to refresh the full data of all thermostats
Map getEcobeeSensors() {	
    LOG("====> getEcobeeSensors() entered. thermostats: ${thermostats}", 5)

	def sensorMap = [:]
    def foundThermo = null
	// TODO: Is this needed?
	atomicState.remoteSensors = [:]    

	// Now that we routinely only collect the data that has changed in atomicState.thermostatData, we need to ALWAYS refresh that data
	// here so that we are sure we have everything we need here.
	getEcobeeThermostats()
	
	atomicState.thermostatData.thermostatList.each { singleStat ->
        def tid = singleStat.identifier
		LOG("thermostat loop: singleStat.identifier == ${tid} -- singleStat.remoteSensors == ${singleStat.remoteSensors} ", 4)   
        def tempSensors = atomicState.remoteSensors
    	if (!settings.thermostats.findAll{ it.contains(tid) } ) {
        	// We can skip this thermostat as it was not selected by the user
            LOG("getEcobeeSensors() --> Skipping this thermostat: ${tid}", 5)
        } else {
        	LOG("getEcobeeSensors() --> Entering the else... we found a match. singleStat == ${singleStat.name}", 4)
                        
        	// atomicState.remoteSensors[tid] = atomicState.remoteSensors[tid] ? (atomicState.remoteSensors[tid] + singleStat.remoteSensors) : singleStat.remoteSensors
            tempSensors[tid] = tempSensors[tid] ? tempSensors[tid] + singleStat.remoteSensors : singleStat.remoteSensors
            LOG("After atomicState.remoteSensors setup...", 5)	        
                        
            LOG("getEcobeeSensors() - singleStat.remoteSensors: ${singleStat.remoteSensors}", 4)
            LOG("getEcobeeSensors() - atomicState.remoteSensors: ${atomicState.remoteSensors}", 4)
		}
        atomicState.remoteSensors = tempSensors
        
		// WORKAROUND: Iterate over remoteSensors list and add in the thermostat DNI
		// 		 This is needed to work around the dynamic enum "bug" which prevents proper deletion
        LOG("remoteSensors all before each loop: ${atomicState.remoteSensors}", 5, null, "trace")
		atomicState.remoteSensors[tid].each {
        	LOG("Looping through each remoteSensor. Current remoteSensor: ${it}", 5, null, "trace")
			if (it.type == "ecobee3_remote_sensor") {
            	LOG("Adding an ecobee3_remote_sensor: ${it}", 4, null, "trace")
				def value = "${it?.name}"
				def key = "ecobee_sensor-"+ it?.id + "-" + it?.code
				sensorMap["${key}"] = value
			} else if ( (it.type == "thermostat") && (settings.showThermsAsSensor == true) ) {            	
				LOG("Adding a Thermostat as a Sensor: ${it}", 4, null, "trace")
           	    def value = "${it?.name}"
				def key = "ecobee_sensor_thermostat-"+ it?.id + "-" + it?.name
       	       	LOG("Adding a Thermostat as a Sensor: ${it}, key: ${key}  value: ${value}", 4, null, "trace")
				sensorMap["${key}"] = value + " (Thermostat)"
       	   	} else if ( it.type == "control_sensor" && it.capability[0]?.type == "temperature") {
       	   		// We can add this one as it supports temperature
       	      	LOG("Adding a control_sensor: ${it}", 4, null, "trace")
				def value = "${it?.name}"
				def key = "control_sensor-"+ it?.id
				sensorMap["${key}"] = value    
           	} else {
           		LOG("Did NOT add: ${it}. settings.showThermsAsSensor=${settings.showThermsAsSensor}", 4, null, "trace")
           	}
		}
	} // end thermostats.each loop
	
    LOG("getEcobeeSensors() - remote sensor list: ${sensorMap}", 4)
    atomicState.eligibleSensors = sensorMap
    atomicState.numAvailSensors = sensorMap.size() ?: 0
	return sensorMap
}
     
def getThermostatDisplayName(stat) {
	if(stat?.name)
		return stat.name.toString()
	return (getThermostatTypeName(stat) + " (${stat.identifier})").toString()
}

def getThermostatTypeName(stat) {
	return stat.modelNumber == "siSmart" ? "Smart Si" : "Smart"
}

def installed() {
	LOG("Installed with settings: ${settings}", 4)	
	initialize()
}

def updated() {	
    LOG("Updated with settings: ${settings}", 4)	
    atomicState.Events = [:]
    atomicState.Eventss = [:]
    atomicState.aTestMap = [:]
    
    if (!atomicState.atomicMigrate) {
    	LOG("updated() - Migrating state to atomicState...", 2, null, "warn")        
        try {
        	state.collect {
            	LOG("traversing state: ${it} name: ${it.key}  value: ${it.value}")
                atomicState."${it.key}" = it.value
            }			
            atomicState.atomicMigrate = true
        } catch (Exception e) {
        	LOG("updated() - Migration of state t- atomicState failed with exception (${e})", 1, null, "error")            
        }
        try {
        	LOG("atomicState after migration", 4)
        	atomicState.collect {
	        	LOG("Traversing atomicState: ${it} name: ${it.key}  value: ${it.value}")
    	    }
		} catch (Exception e) {
        	LOG("Unable to traverse atomicState", 2, null, "warn")
        }
        atomicState.atomicMigrate = true
    }
    initialize()
}

def initialize() {	
    LOG("=====> initialize()", 4)    
    
    atomicState.connected = "full"        
    atomicState.reAttempt = 0
    atomicState.reAttemptPoll = 0
    
	try {
		unsubscribe()
    	unschedule() // reset all the schedules
	} catch (Exception e) {
    	LOG("updated() - Exception encountered trying to unschedule(). Exception: ${e}", 2, null, "error")
    }    
    
    def nowTime = now()
    def nowDate = getTimestamp()
    
    // Initialize several variables    
	atomicState.lastScheduledPoll = nowTime
    atomicState.lastScheduledPollDate = nowDate
    atomicState.lastScheduledWatchdog = nowTime
    atomicState.lastScheduledWatchdogDate = nowDate
	atomicState.lastPoll = nowTime
    atomicState.lastPollDate = nowDate    
    atomicState.lastWatchdog = nowTime
    atomicState.lastWatchdogDate = nowDate
    atomicState.lastUserDefinedEvent = now()
    atomicState.lastUserDefinedEventDate = getTimestamp()  
    atomicState.lastRevisions = "foo"
    atomicState.latestRevisions = "bar"
    atomicState.skipCount = 0
    atomicState.getWeather = true
    atomicState.runtimeUpdated = true
    atomicState.thermostatUpdated = true
    atomicState.forcePoll= true				// make sure we get ALL the data after initialization
	atomicState.lastEquipStatus = null
	atomicState.lastEquipOpStat = null
    
    atomicState.timeOfDay = getTimeOfDay()
    
    def sunriseAndSunset = getSunriseAndSunset()
    // LOG("sunriseAndSunset == ${sunriseAndSunset}")
    if(location.timeZone) {
        atomicState.sunriseTime = sunriseAndSunset.sunrise.format("HHmm", location.timeZone).toDouble()
        atomicState.sunsetTime = sunriseAndSunset.sunset.format("HHmm", location.timeZone).toDouble()
    } else if( (sunriseAndSunset !=  [:]) && (location != null) ) {
        atomicState.sunriseTime = sunriseAndSunset.sunrise.format("HHmm").toDouble()
        atomicState.sunsetTime = sunriseAndSunset.sunset.format("HHmm").toDouble()
    } else {
    	atomicState.sunriseTime = "0500".toDouble()
        atomicState.sunsetTime = "1800".toDouble()
    }
	    
    // Setup initial polling and determine polling intervals
	atomicState.pollingInterval = getPollingInterval()
    atomicState.watchdogInterval = 15
    atomicState.reAttemptInterval = 15 // In seconds
	
    if (state.initialized) {		
    	// refresh Thermostats and Sensor full lists
    	getEcobeeThermostats()
    	getEcobeeSensors()
    } 
   
    // Children
    def aOK = true
	if (settings.thermostats?.size() > 0) { aOK = aOK && createChildrenThermostats() }
	if (settings.ecobeesensors?.size() > 0) { aOK = aOK && createChildrenSensors() }    
    deleteUnusedChildren()
   
	// Initial poll()
    if (settings.thermostats?.size() > 0) { pollInit() }

    // Add subscriptions as little "daemons" that will check on our health    
    subscribe(location, scheduleWatchdog)
    subscribe(location, "routineExecuted", scheduleWatchdog)
    subscribe(location, "sunset", sunsetEvent)
    subscribe(location, "sunrise", sunriseEvent)
    subscribe(location, "position", scheduleWatchdog)
    
    if ( settings.useWatchdogDevices == true ) {
    	if ( settings.watchdogBattery?.size() > 0) { subscribe(settings.watchdogBattery, "battery", userDefinedEvent) }
        if ( settings.watchdogHumidity?.size() > 0) { subscribe(settings.watchdogHumidity, "humidity", userDefinedEvent) }
        if ( settings.watchdogLuminance?.size() > 0) { subscribe(settings.watchdogLuminance, "illuminance", userDefinedEvent) }
        if ( settings.watchdogMotion?.size() > 0) { subscribe(settings.watchdogMotion, "motion", userDefinedEvent) }
        if ( settings.watchdogSwitch?.size() > 0) { subscribe(settings.watchdogSwitch, "switch", userDefinedEvent) }
        if ( settings.watchdogTemp?.size() > 0) { subscribe(settings.watchdogTemp, "temperature", userDefinedEvent) }    
    }    
    
    // Schedule the various handlers
    LOG("Spawning scheduled events from initialize()", 5, null, "trace")
    if (settings.thermostats?.size() > 0) { 
    	LOG("Spawning the poll scheduled event. (settings.thermostats?.size() - ${settings.thermostats?.size()})", 4)
    	spawnDaemon("poll", false) 
	} 
    spawnDaemon("watchdog", false)
        
    //send activity feeds to tell that device is connected
    def notificationMessage = aOK ? "is connected to SmartThings" : "had an error during setup of devices"
    sendActivityFeeds(notificationMessage)
    atomicState.timeSendPush = null
    if (!atomicState.initialized) {
    	atomicState.initialized = true
        // These two below are for debugging and statistics purposes
        atomicState.initializedEpic = nowTime
        atomicState.initializedDate = nowDate
	}
    return aOK
}

private def createChildrenThermostats() {
	LOG("createChildrenThermostats() entered: thermostats=${settings.thermostats}", 5)
    // Create the child Thermostat Devices
	def devices = settings.thermostats.collect { dni ->
		def d = getChildDevice(dni)
		if(!d) {        	
            try {
				d = addChildDevice(app.namespace, getChildThermostatName(), dni, null, ["label":"EcoTherm: ${atomicState.thermostatsWithNames[dni]}", completedSetup:true])			
			} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
            	LOG("You MUST add the ${getChildSensorName()} Device Handler to the IDE BEFORE running the setup.", 1, null, "error")
                return false
            }
            LOG("created ${d.displayName} with id $dni", 4)
		} else {
			LOG("found ${d.displayName} with id $dni already exists", 4)            
		}
		return d
	}
    LOG("Created/Updated ${devices.size()} thermostats")    
    return true
}

private def createChildrenSensors() {
	LOG("createChildrenSensors() entered: ecobeesensors=${settings.ecobeesensors}", 5)
    // Create the child Ecobee Sensor Devices
	def sensors = settings.ecobeesensors.collect { dni ->
		def d = getChildDevice(dni)
		if(!d) {        	
            try {
				d = addChildDevice(app.namespace, getChildSensorName(), dni, null, ["label":"EcoSensor: ${atomicState.eligibleSensors[dni]}", completedSetup:true])
			} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
            	LOG("You MUST add the ${getChildSensorName()} Device Handler to the IDE BEFORE running the setup.", 1, null, "error")
                return false
            }
            LOG("created ${d.displayName} with id $dni", 4)
		} else {
        	LOG("found ${d.displayName} with id $dni already exists", 4)
		}
		return d
	}
	LOG("Created/Updated ${sensors.size()} sensors.")
    return true
}

// NOTE: For this to work correctly getEcobeeThermostats() and getEcobeeSensors() should be called prior
private def deleteUnusedChildren() {
	LOG("deleteUnusedChildren() entered", 5)    
    
    if (settings.thermostats?.size() == 0) {
    	// No thermostats, need to delete all children
        LOG("Deleting All My Children!", 2, null, "warn")
    	getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }        
    } else {
    	// Only delete those that are no longer in the list
        // This should be a combination of any removed thermostats and any removed sensors
        def allMyChildren = getAllChildDevices()
        LOG("These are currently all of my childred: ${allMyChildren}", 5, null, "debug")
        
        // Update list of "eligibleSensors"       
        def childrenToKeep = (thermostats ?: []) + (atomicState.eligibleSensors?.keySet() ?: [])
        LOG("These are the children to keep around: ${childrenToKeep}", 4, null, "trace")
        
    	def childrenToDelete = allMyChildren.findAll { !childrenToKeep.contains(it.deviceNetworkId) }        
        
        LOG("Ready to delete these devices. ${childrenToDelete}", 4, null, "trace")
		if (childrenToDelete.size() > 0) childrenToDelete?.each { deleteChildDevice(it.deviceNetworkId) } //inherits from SmartApp (data-management)    
    }    
}

def sunriseEvent(evt) {
	LOG("sunriseEvent() - with evt (${evt?.name}:${evt?.value})", 4, null, "info")
	atomicState.timeOfDay = "day"
    atomicState.lastSunriseEvent = now()
    atomicState.lastSunriseEventDate = getTimestamp()
    if(location.timeZone) {
    	atomicState.sunriseTime = new Date().format("HHmm", location.timeZone).toInteger()
    } else {
    	atomicState.sunriseTime = new Date().format("HHmm").toInteger()
    }
    scheduleWatchdog(evt, true)    
}

def sunsetEvent(evt) {
	LOG("sunsetEvent() - with evt (${evt?.name}:${evt?.value})", 4, null, "info")
	atomicState.timeOfDay = "night"
    atomicState.lastSunsetEvent = now()
    atomicState.lastSunsetEventDate = getTimestamp()
    if(location.timeZone) {
    	atomicState.sunsetTime = new Date().format("HHmm", location.timeZone).toInteger()
	} else {
    	atomicState.sunsetTime = new Date().format("HHmm").toInteger()
    }
    scheduleWatchdog(evt, true)
}

// Event on a monitored device - ignore the frequency and just go ahead and poll, since pollChildren will throttle if nothing has changed.
def userDefinedEvent(evt) {
	LOG("userDefinedEvent() - with evt (Device:${evt?.displayName} ${evt?.name}:${evt?.value})", 4, null, "info")
    atomicState.lastUserDefinedEventDate = getTimestamp()
    atomicState.lastUserDefinedEventInfo = "Event Info: (Device:${evt?.displayName} ${evt?.name}:${evt?.value})" 
    
    if ( ((now() - atomicState.lastUserDefinedEvent) / 1000.0 / 60.0) < 0.5 ) { 
    	LOG("userDefinedEvent() - time since last event is less than 30 seconds. Exiting without polling.", 4)
    	return 
 	} 
	poll()
    atomicState.lastUserDefinedEvent = now()
    atomicState.lastUserDefinedEventDate = getTimestamp()
    
	if ( ((now() - atomicState.lastUserDefinedEvent) / 1000 / 60) < 3 ) { 
    	LOG("userDefinedEvent() - polled, but time since last event is less than 3 minutes. Exiting without performing additional actions.", 4)
    	return 
 	}    
    scheduleWatchdog(evt, true)
}

def scheduleWatchdog(evt=null, local=false) {
	def results = true    
    LOG("scheduleWhatchdog() called with: evt (${evt?.name}:${evt?.value}) & local (${local})", 4, null, "trace")
    // Only update the Scheduled timestamp if it is not a local action or from a subscription
    if ( (evt == null) && (local==false) ) {
    	atomicState.lastScheduledWatchdog = now()
        atomicState.lastScheduledWatchdogDate = getTimestamp()
        atomicState.getWeather = true								// next pollEcobeeApi for runtime changes should also get the weather object
	}
    
    // Check to see if we have called too soon
    def timeSinceLastWatchdog = (now() - state.lastWatchdog) / 1000 / 60
    if ( timeSinceLastWatchdog < 1 ) {
    	LOG("It has only been ${timeSinceLastWatchdog} since last scheduleWatchdog was called. Please come back later.", 2, null, "trace")
        return true
    }
    
    atomicState.lastWatchdog = now()
    atomicState.lastWatchdogDate = getTimestamp()
    
    LOG("After watchdog tagging")
	if(apiConnected() == "lost") {
    	// Possibly a false alarm? Check if we can update the token with one last fleeting try...
        if( refreshAuthToken() ) { 
        	// We are back in business!
			LOG("scheduleWatchdog() - Was able to recover the lost connection. Please ignore any notifications received.", 1, null, "error")
        } else {        
			LOG("scheduleWatchdog() - Unable to schedule handlers do to loss of API Connection. Please ensure you are authorized.", 1, null, "error")
			return false
		}
	}
    
	def pollAlive = isDaemonAlive("poll")
    def watchdogAlive = isDaemonAlive("watchdog")
    
    LOG("scheduleWatchdog() --> pollAlive==${pollAlive}  watchdogAlive==${watchdogAlive}", 4, null, "debug")
    
    // Reschedule polling if it has been a while since the previous poll    
    if (!pollAlive) { spawnDaemon("poll") }
    if (!watchdogAlive) { spawnDaemon("watchdog") }

    return true
}

// Watchdog Checker
private def Boolean isDaemonAlive(daemon="all") {
	// Daemon options: "poll", "auth", "watchdog", "all"    
    def daemonList = ["poll", "auth", "watchdog", "all"]

	daemon = daemon.toLowerCase()
    def result = true    
    		
    def timeSinceLastScheduledPoll = (atomicState.lastScheduledPoll == 0 || atomicState.lastScheduledPoll == null) ? 0 : ((now() - atomicState.lastScheduledPoll) / 1000 / 60)  // TODO: Removed toDouble() will this impact?
    def timeSinceLastScheduledWatchdog = (atomicState.lastScheduledWatchdog == 0 || atomicState.lastScheduledWatchdog == null) ? 0 : ((now() - atomicState.lastScheduledWatchdog) / 1000 / 60)
	def timeBeforeExpiry = atomicState.authTokenExpires ? ((atomicState.authTokenExpires - now()) / 1000 / 60) : 0
    
    LOG("isDaemonAlive() - now() == ${now()} for daemon (${daemon})", 5, null, "trace")
    LOG("isDaemonAlive() - Time since last poll? ${timeSinceLastScheduledPoll} -- atomicState.lastScheduledPoll == ${atomicState.lastScheduledPoll}", 4, null, "info")
    LOG("isDaemonAlive() - Time since watchdog activation? ${timeSinceLastScheduledWatchdog} -- atomicState.lastScheduledWatchdog == ${atomicState.lastScheduledWatchdog}", 4, null, "info")
    LOG("isDaemonAlive() - Time left (timeBeforeExpiry) until expiry (in min): ${timeBeforeExpiry}", 4, null, "info")
        
    if (daemon == "poll" || daemon == "all") {
    	LOG("isDaemonAlive() - Checking daemon (${daemon}) in 'poll'", 4, null, "trace")
        def maxInterval = atomicState.pollingInterval + 2
        if ( timeSinceLastScheduledPoll >= maxInterval ) { result = false }
	}	
    
    if (daemon == "watchdog" || daemon == "all") {
    	LOG("isDaemonAlive() - Checking daemon (${daemon}) in 'watchdog'", 4, null, "trace")
        def maxInterval = atomicState.watchdogInterval + 2
        LOG("isDaemonAlive(watchdog) - timeSinceLastScheduledWatchdog=(${timeSinceLastScheduledWatchdog})  Timestamps: (${atomicState.lastScheduledWatchdogDate}) (epic: ${state.lastScheduledWatchdog}) now-(${now()})", 4, null, "trace")
        if ( timeSinceLastScheduledWatchdog >= maxInterval ) { result = false }
    }
    
	if (!daemonList.contains(daemon) ) {
    	// Unkown option passed in, gotta punt
        LOG("isDaemonAlive() - Unknown daemon: ${daemon} received. Do not know how to check this daemon.", 1, null, "error")
        result = false
    }
    LOG("isDaemonAlive() - result is ${result}", 4, null, "trace")
    return result
}

private def Boolean spawnDaemon(daemon="all", unsched=true) {
	// Daemon options: "poll", "auth", "watchdog", "all"    
    def daemonList = ["poll", "auth", "watchdog", "all"]
    
    daemon = daemon.toLowerCase()
    def result = true
    
    if (daemon == "poll" || daemon == "all") {
    	LOG("spawnDaemon() - Performing seance for daemon (${daemon}) in 'poll'", 4, null, "trace")
        // Reschedule the daemon
        try {
            if( unsched ) { unschedule("pollScheduled") }
            if ( canSchedule() ) { 
            	LOG("Polling Interval == ${atomicState.pollingInterval}", 4)
            	if (atomicState.pollingInterval.toInteger() <= 3) {
                	LOG("Using schedule instead of runEvery with atomicState.pollingInterval: ${atomicState.pollingInterval}", 4)
                	schedule("* 0/${atomicState.pollingInterval} * * * ?", "pollScheduled")                    
                } else {
                	LOG("Using runEvery to setup polling with atomicState.pollingInterval: ${atomicState.pollingInterval}", 4)
        			"runEvery${atomicState.pollingInterval}Minutes"("pollScheduled")
                }
            	result = pollScheduled() && result
			} else {
            	LOG("canSchedule() is NOT allowed or result already false! Unable to schedule poll daemon!", 1, null, "error")
        		result = false
        	}
        } catch (Exception e) {
        	LOG("spawnDaemon() - Exception when performing spawn for ${daemon}. Exception: ${e}", 1, null, "error")
            result = false
        }		
    }
    
    if (daemon == "watchdog" || daemon == "all") {
    	LOG("spawnDaemon() - Performing seance for daemon (${daemon}) in 'watchdog'", 4, null, "trace")
        // Reschedule the daemon
        try {
            if( unsched ) { unschedule("scheduleWatchdog") }
            if ( canSchedule() ) { 
        		"runEvery${atomicState.watchdogInterval}Minutes"("scheduleWatchdog")
            	result = result && true
			} else {
            	LOG("canSchedule() is NOT allowed or result already false! Unable to schedule daemon!", 1, null, "error")
        		result = false
        	}
        } catch (Exception e) {
        	LOG("spawnDaemon() - Exception when performing spawn for ${daemon}. Exception: ${e}", 1, null, "error")
            result = false
        }		
        atomicState.getWeather = true	// next pollEcobeeApi for runtime changes should also get the weather object
    }
    
    if (!daemonList.contains(daemon) ) {
    	// Unkown option passed in, gotta punt
        LOG("isDaemonAlive() - Unknown daemon: ${daemon} received. Do not know how to check this daemon.", 1, null, "error")
        result = false
    }
    return result
}

def updateLastPoll(Boolean isScheduled=false) {
	if (isScheduled) {
    	atomicState.lastScheduledPoll = now()
        atomicState.lastScheduledPollDate =  getTimestamp()
    } else {
    	atomicState.lastPoll = now()
        atomicState.lastPollDate = getTimestamp()
    }
}

// Called by scheduled() event handler
def pollScheduled() {
	updateLastPoll(true)
	LOG("pollScheduled() - Running at ${atomicState.lastScheduledPollDate} (epic: ${atomicState.lastScheduledPoll})", 3, null, "trace")    
    return poll()
}

// Called during initialization to get the inital poll
def pollInit() {
	LOG("pollInit()", 5)
    atomicState.forcePoll = true // Initialize the variable and force a poll even if there was one recently    
	pollChildren(null) // Hit the ecobee API for update on all thermostats
}

def pollChildren(child = null) {
	def results = true   
    
	LOG("=====> pollChildren() - atomicState.forcePoll(${atomicState.forcePoll})  atomicState.lastPoll(${atomicState.lastPoll})  now(${now()})  atomicState.lastPollDate(${state.lastPollDate})", 4, child, "trace")
    
	if(apiConnected() == "lost") {
    	// Possibly a false alarm? Check if we can update the token with one last fleeting try...
        LOG("apiConnected() == lost, try to do a recovery, else we are done...", 3, child, "debug")
        if( refreshAuthToken() ) { 
        	// We are back in business!
			LOG("pollChildren() - Was able to recover the lost connection. Please ignore any notifications received.", 1, child, "error")
        } else {        
			LOG("pollChildren() - Unable to schedule handlers do to loss of API Connection. Please ensure you are authorized.", 1, child, "error")
			return false
		}
	}

    // Run a watchdog checker here
    scheduleWatchdog(null, true)    
    
    if (settings.thermostats?.size() < 1) {
    	LOG("pollChildren() - Nothing to poll as there are no thermostats currently selected", 1, child, "warn")
		return true
    }    
    
    def timeSinceLastPoll 
    boolean somethingChanged = true
    String thermostatsToPoll = getChildThermostatDeviceIdsString()
    if (child == null) { // normal call
   		// Check to see if it is time to do an full poll to the Ecobee servers. If so, execute the API call and update ALL children
    	timeSinceLastPoll = atomicState.forcePoll ? 999.9 : ((now() - atomicState.lastPoll?.toDouble()) / 1000 / 60) 
    	LOG("Time since last poll? ${timeSinceLastPoll} -- atomicState.lastPoll == ${atomicState.lastPoll}", 3, child, "info")
    
    	// Also, check if anything has changed in the thermostatSummary (really don't need to call EcobeeAPI if it hasn't).
    	somethingChanged = checkThermostatSummary(thermostatsToPoll)
	} else {
    	atomicState.forcePoll = true	// called by a child - do a forcePoll
    }
    
    if (atomicState.forcePoll  || somethingChanged) {
    	// It has been longer than the minimum delay OR some thermostat data has changed OR we are doing a forced poll
        LOG("pollChildren() - Getting changes", 3, child)
    	pollEcobeeAPI(thermostatsToPoll)  // This will update the values saved in the state which can then be used to send the updates
	} else {
        LOG("pollChildren() - Nothing has changed.", 3, child)
        // generateEventLocalParams() // Update any local parameters and send
    }
	
    if (somethingChanged) {
		// Iterate over all the children
		def d = getChildDevices()
    	d?.each() { oneChild ->
    		LOG("pollChildren() - Processing poll data for child: ${oneChild} has ${oneChild.capabilities}", 4)
        
    		if( oneChild.hasCapability("Thermostat") ) {
        		// We found a Thermostat, send all of its events
            	LOG("pollChildren() - We found a Thermostat!", 5)
            	oneChild.generateEvent(atomicState.thermostats[oneChild.device.deviceNetworkId]?.data)
        	} else {
        		// We must have a remote sensor
            	LOG("pollChildren() - Updating sensor data for ${oneChild}: ${oneChild.device.deviceNetworkId} data: ${atomicState.remoteSensorsData[oneChild.device.deviceNetworkId]?.data}", 4)
            	oneChild.generateEvent(atomicState.remoteSensorsData[oneChild.device.deviceNetworkId]?.data)
			}
		}
    }
    return results
}

private def generateEventLocalParams() {
	// Iterate over all the children
    LOG("generateEventLocalParams() - sending cached data", 3, "", "info")
	def d = getChildDevices()
    d?.each() { oneChild ->
    	LOG("generateEventLocalParams() - Processing poll data for child: ${oneChild} has ${oneChild.capabilities}", 4)
        
    	if( oneChild.hasCapability("Thermostat") ) {
        	// We found a Thermostat, send local params as events
            LOG("generateEventLocalParams() - We found a Thermostat!", 4)
            def data = [
            	apiConnected: apiConnected()
            ]
            
            atomicState.thermostats[oneChild.device.deviceNetworkId]?.data?.apiConnected = apiConnected()            
            oneChild.generateEvent(data)
        } else {
        	// We must have a remote sensor
            LOG("generateEventLocalParams() - Updating sensor data: ${oneChild.device.deviceNetworkId}", 4)
			// No local params to send            
        } 
    }
}

// Checks if anything has changed since the last time this routine was called, using lightweight thermostatSummary poll
// NOTE: Despite the documentation, Runtime Revision CAN change more frequently than every 3 minutes - equipmentStatus is 
//       apparently updated in real time (or at least very close to real time)
private boolean checkThermostatSummary(thermostatIdsString) {

	def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + thermostatIdsString + '","includeEquipmentStatus":"false"}}'
    // LOG("checkThermostatSummary() - jsonRequestBody is: ${jsonRequestBody}", 4)
	
	def pollParams = [
			uri: apiEndpoint,
			path: "/1/thermostatSummary",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
			query: [format: 'json', body: jsonRequestBody]
	]
	
    def result = false
	def statusCode=999
	int j=0        
	
	while ((statusCode != 0) && (j++ <2)) { // retries once if api call fails
		try {
			httpGet(pollParams) { resp ->
				if(resp.status == 200) {
					LOG("checkThermostatSummary() poll results returned resp.data ${resp.data}", 4)
					statusCode = resp.data.status.code
					if (statusCode == 0) { 
                    	def revisions = resp.data.revisionList
						def thermostatUpdated = false
						def runtimeUpdated = false
                        String tstatsStr = ""
						result = true
						if (atomicState.lastRevisions == "foo") { // haven't finished (re)initializing yet
                            thermostatUpdated = true
                            runtimeUpdated = true
                            tstatsStr = thermostatIdsString // act like all the thermostats have changed
                        } else {
							result = false
							for (i in 0..resp.data.thermostatCount - 1) {
						    	def lastDetails = atomicState.lastRevisions[i].split(':')
						    	def latestDetails = revisions[i].split(':')
                            	def tru = false
                                def ttu = false
								if (lastDetails[0] == latestDetails[0]) {	// verify these are the same thermostat
									if (lastDetails[5] != latestDetails[5]) tru = true 
									if (lastDetails[3] != latestDetails[3]) ttu = true 
                                } else {
                                    tru = true // IDs didn't match, so assume everything changed for this thermostat
                                    ttu = true
                                }
                                if (tru || ttu) {
                                	runtimeUpdated = (runtimeUpdated || tru)
                                    thermostatUpdated = (thermostatUpdated || ttu)
                                    result = true
                                    tstatsStr = (tstatsStr=="") ? "${latestDetails[0]}" : (tstatsStr.contains("${latestDetails[0]}")) ? tstatsStr : tstatsStr + ",${latestDetails[0]}"
                                }
							}
						}
						atomicState.latestRevisions = revisions			// let pollEcobeeAPI update last with latest after it finishes the poll
                        atomicState.thermostatUpdated = thermostatUpdated	// Revised: settings, program, event, device
						atomicState.runtimeUpdated = runtimeUpdated		// Revised: runtime, equip status, remote sensors, weather?
                        atomicState.changedThermostatIds = tstatsStr    // only these thermostats need to be requested in pollEcobeeAPI
					}
                    // if we get here, we had http status== 200, but API status != 0
				} else {
					LOG("checkThermostatSummary() - polling got http status ${resp.status}", 1, null, "error")
				}
			}
		} catch (groovyx.net.http.HttpResponseException e) {    
        	LOG("checkThermostatSummary()  HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}", 1, null, "error")
        	result = false
         	if (e.response.data.status.code == 14) {
            	atomicState.action = "pollChildren"
            	LOG( "Refreshing your auth_token!", 4)
            	if ( refreshAuthToken() ) { result = true } else { result = false }
        	}
			atomicState.forcePoll = true		// make pollEcobeeAPI poll anyway
    	} catch (java.util.concurrent.TimeoutException e) {
    		LOG("checkThermostatSummary(), TimeoutException: ${e}.", 1, null, "warn")
        	// Do not add an else statement to run immediately as this could cause an long looping cycle if the API is offline
        	if ( canSchedule() ) { runIn(atomicState.reAttemptInterval, "pollChildren") }
       	 	result = false    
    	}
	}
    LOG("<===== Leaving checkThermostatSummary() result: ${result}", 2, null, "info")
	return result
}

private def pollEcobeeAPI(thermostatIdsString = "") {
	LOG("=====> pollEcobeeAPI() entered - thermostatIdsString = ${thermostatIdsString}", 2, null, "info")

	boolean forcePoll = atomicState.forcePoll		// lightweight way to use atomicStates that we don't want to change under us
    boolean thermostatUpdated = atomicState.thermostatUpdated
    boolean runtimeUpdated = atomicState.runtimeUpdated
    boolean getWeather = atomicState.getWeather
	boolean somethingChanged

	// forcePoll = true
	
	if (!forcePoll) {
		// lets find out what has changed
    	if (atomicState.lastRevisions != atomicState.latestRevisions) {
    		somethingChanged = true	// we already know there are changes
    	} else {
    		somethingChanged = checkThermostatSummary(thermostatIdsString)
    	}
    	// if nothing has changed, and this isn't a forced poll, just return (keep count of polls we have skipped)
    	// This probably can't happen anymore...shouldn't event be here if nothing has changed and not a forced poll...
    	if (!somethingChanged) {
    		LOG("<===== Leaving pollEcobeeAPI() - nothing changed, skipping heavy poll (${atomicState.skipCount})", 2, null, "info")
        	return true		// act like we completed the heavy poll without error
		}
	}
    
    // Let's only check those thermostats that actually changed...unless this is a forcePoll - or if we are getting the weather 
    // (we need to get weather for all therms at the same time, because we only update every 15 minutes and use the cached version
    // the rest of the time)
    String checkTherms = (forcePoll || (runtimeUpdated && getWeather)) ? thermostatIdsString : atomicState.changedThermostatIds
    checkTherms = (checkTherms) ? checkTherms : thermostatIdsString
	LOG("pollEcobeeAPI() - checking thermostats ${checkTherms}", 3)
    
	def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + checkTherms + '"'
	
	if (forcePoll || thermostatUpdated) {
		jsonRequestBody += ',"includeSettings":"true","includeProgram":"true","includeEvents":"true"'
		LOG("pollEcobeeAPI() - getting thermostat", 3)
	}
	if (forcePoll || runtimeUpdated) {
		jsonRequestBody += ',"includeRuntime":"true","includeEquipmentStatus":"true"'
        String gw = ''
        // only get sensorData if we have any sensors configured
		if (forcePoll || settings.ecobeesensors?.size() > 0) {
			jsonRequestBody += ',"includeSensors":"true"'
			gw = ' & sensors'
		}
        if (forcePoll || getWeather) {
        	jsonRequestBody += ',"includeWeather":"true"'		// time to get the weather report (only changes every 15 minutes or so - watchdog sets this when it runs)
            gw += ' & weather'
        }

		LOG("pollEcobeeAPI() - getting runtime" + gw, 3)
	}
	jsonRequestBody += '}}'
	
	// TODO: Check on any running EVENTs on thermostat	
	//jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + thermostatIdsString + '","includeExtendedRuntime":"false","includeSettings":"true","includeRuntime":"true","includeEquipmentStatus":"true","includeSensors":"true","includeWeather":"true","includeProgram":"true","includeAlerts":"false","includeEvents":"true"}}'
 	//                 	   {"selection":{"selectionType":"thermostats","selectionMatch":"XXX,YYY",                                                     "includeSettings":"true","includeRuntime":"true","includeEquipmentStatus":"true","includeSensors":"true","includeProgram":"true","includeWeather":"true","includeAlerts":"true","includeEvents":"true"}}
    
	LOG("pollEcobeeAPI() - jsonRequestBody is: ${jsonRequestBody}", 3)
 
	atomicState.forcePoll = false	// it's ok to clear the flag now
    def result = false
	
	def pollParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
			query: [format: 'json', body: jsonRequestBody]
	]

	try{
		httpGet(pollParams) { resp ->
			if(resp.status == 200) {
				// LOG("poll results returned resp.data ${resp.data}", 2)
				if (resp.data) atomicState.thermostatData = resp.data		// this now only stores the most recent collection of changed data
															// this may not be the entire set of data, so the rest of this code will
															// calculate updates from the individual data objects (which always include
															// the latest values recieved

                // Update the atomicState caches for each received data object(s)
                def tempSettings = [:]
                def tempProgram = [:]
                def tempEvents = [:]
                def tempRuntime = [:]
                def tempWeather = [:]
                def tempSensors = [:]
                def tempEquipStat = [:]
                
                // collect the returned data into temporary individual caches (because we can't update individual Map items in an atomicState Map)
                resp.data.thermostatList.each { stat ->
					String tid = stat.identifier.toString()
                    if (forcePoll || thermostatUpdated) {
                        if (stat.settings) tempSettings[tid] = stat.settings		// don't overwrite data objects if not requested/returned
                        if (stat.program) tempProgram[tid] = stat.program
                        if (stat.events) tempEvents[tid] = stat.events
                    }
 					if (forcePoll || runtimeUpdated) {
 						if (stat.runtime) tempRuntime[tid] = stat.runtime
                        if (stat.remoteSensors) tempSensors[tid] = stat.remoteSensors // should be blank unless we requested it specifically
                        if (stat.weather) tempWeather[tid] = stat.weather
                        tempEquipStat[tid] = stat.equipmentStatus // always store ("" is a valid return value)
                    }
                }
                
                def numTherms = atomicState.settingsCurrentTherms.size()
                if (forcePoll || thermostatUpdated) {
                	if (tempSettings != [:]) {								// ignore settings cache if no data retrieved
                   		if (tempSettings.size() != numTherms) tempSettings = atomicState.settings + tempSettings // got less than all, just add new to the cached Map
                   		atomicState.settings = tempSettings
                	}
                	if (tempProgram != [:]) {								// ignore program cache if no data retrieved
                   		if (tempProgram.size() != numTherms) tempProgram = atomicState.program + tempProgram // got less than all, just add new to the cached Map
                   		atomicState.program = tempProgram
                	}
                	if (tempEvents != [:]) {								// ignore events cache if no data retrieved
                   		if (tempEvents.size() != numTherms) tempEvents = atomicState.events + tempEvents // got less than all, just add new to the cached Map
                   		atomicState.events = tempEvents
                	}                    
                }
                if (forcePoll || runtimeUpdated) {
                    if (tempRuntime != [:]) {
                    	if (tempRuntime.size() != numTherms) tempRuntime = atomicState.runtime + tempRuntime // get less than all, just add to the table
                    	atomicState.runtime = tempRuntime
                    }
                    if (tempSensors != [:]) {
                    	if (tempSensors.size() != numTherms) tempSensors = atomicState.remoteSensors + tempSensors // get less than all, just add to the table
                    	atomicState.remoteSensors = tempSensors
                    }
                    //if (resp.data.thermostatList.remoteSensors) atomicState.remoteSensors = resp.data.thermostatList.remoteSensors
                    
                    if (tempWeather != [:]) {
                    	if (tempWeather.size() != numTherms) tempWeather = atomicState.weather + tempWeather // get less than all, just add to the table
                    	atomicState.weather = tempWeather
                    }
                    if (tempEquipStat != [:]) {
                    	if (tempEquipStat.size() != numTherms) tempEquipStat = atomicState.equipmentStatus + tempEquipStat // get less than all, just add to the table
                    	atomicState.equipmentStatus = tempEquipStat
                    }
				}
                
                updateLastPoll()
               
                // Update the data and send it down to the devices as events
				if (forcePoll || runtimeUpdated) updateSensorData()	// only update if we got updates
                													// TODO: need to figure out how to update only the sensors on thermostats that were updated
                updateThermostatData()  							// update everything else

				result = true
                atomicState.lastRevisions = atomicState.latestRevisions
                if (runtimeUpdated) atomicState.runtimeUpdated = false
                if (thermostatUpdated) atomicState.thermostatUpdated = false
                if (runtimeUpdated && getWeather) atomicState.getWeather = false
                
                if (apiConnected() != "full") {
					apiRestored()
                    generateEventLocalParams() // Update the connection status
                }
                def tNames = resp.data.thermostatList?.name.toString()
                def numStats = atomicState.thermostats?.size()
				LOG("pollEcobeeAPI() httpGet: updated ${numStats} thermostat${(numStats>1)?'s':''}: ${tNames} ${atomicState.thermostats}", 1, null, "info")
			} else {
				LOG("pollEcobeeAPI() - polling children & got http status ${resp.status}", 1, null, "error")

				//refresh the auth token
				if (resp.status == 500 && resp.data.status.code == 14) {
					LOG("Resp.status: ${resp.status} Status Code: ${resp.data.status.code}. Unable to recover", 1, null, "error")
                    // Should not possible to recover from a code 14 but try anyway?
                    
                    apiLost("pollEcobeeAPI() - Resp.status: ${resp.status} Status Code: ${resp.data.status.code}. Unable to recover.")
				}
				else {
					LOG("pollEcobeeAPI() - Other responses received. Resp.status: ${resp.status} Status Code: ${resp.data.status.code}.", 1, null, "error")
				}
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {    
        LOG("pollEcobeeAPI()  HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}", 1, null, "error")
        result = false
         if (e.response.data.status.code == 14) {
            atomicState.action = "pollChildren"
            LOG( "Refreshing your auth_token!", 4)
            if ( refreshAuthToken() ) { result = true } else { result = false }
        }
    } catch (java.util.concurrent.TimeoutException e) {
    	LOG("pollEcobeeAPI(), TimeoutException: ${e}.", 1, null, "warn")
        // Do not add an else statement to run immediately as this could cause an long looping cycle if the API is offline
        if ( canSchedule() ) { runIn(atomicState.reAttemptInterval, "pollChildren") }
        result = false    
    } catch (Exception e) {
    // TODO: Handle "org.apache.http.conn.ConnectTimeoutException" as this is a transient error and shouldn't count against our retries
		LOG("pollEcobeeAPI(): General Exception: ${e}.", 1, null, "error")
        atomicState.reAttemptPoll = atomicState.reAttemptPoll + 1
        if (atomicState.reAttemptPoll > 3) {        
        	apiLost("Too many retries (${atomicState.reAttemptPoll - 1}) for polling.")
            return false
        } else {
        	LOG("Setting up retryPolling")
			def reAttemptPeriod = 15 // in sec
        	if ( canSchedule() ) {
            	runIn(atomicState.reAttemptInterval, "refreshAuthToken") 
			} else { 
            	LOG("Unable to schedule refreshAuthToken, running directly")
            	refreshAuthToken() 
            }
        }    	
    }
    LOG("<===== Leaving pollEcobeeAPI() results: ${result}", 5)
   
	return result
}

// poll() will be called on a regular interval using an runEveryX command
def poll(child) {		
    LOG("poll() - Running at ${getTimestamp()} (epic: ${now()})", 3, child, "trace")

    // Check to see if we are connected to the API or not
    if (apiConnected() == "lost") {
    	LOG("poll() - apiConnected() returned lost. Unable to poll.", 2, null, "warn")
        return false
    }    
	
	LOG("poll() - Polling children with pollChildren(null) (requested by ${child})", 4)
	// if (child) atomicState.forcePoll = true		// if child is requesting the poll, then make sure we update EVERYTHING
	return pollChildren(null) // Poll ALL the children at the same time for efficiency    
}

def updateSensorData() {
	LOG("Entered updateSensorData() ${atomicState.remoteSensors}", 5)
 	def sensorCollector = [:]
    
    atomicState.thermostatData.thermostatList.each { singleStat ->
    	def tid = singleStat.identifier
    
		atomicState.remoteSensors[tid].each { 
			if ( ( it.type == "ecobee3_remote_sensor" ) || (it.type == "control_sensor") || ((it.type == "thermostat") && (settings.showThermsAsSensor)) ) {
				// Add this sensor to the list
				def sensorDNI 
               	if (it.type == "ecobee3_remote_sensor") { 
               		sensorDNI = "ecobee_sensor-" + it?.id + "-" + it?.code 
				} else if (it.type == "control_sensor") {
               		LOG("We have a Smart SI style control_sensor! it=${sensor}", 4, null, "trace")
                   	sensorDNI = "control_sensor-" + it?.id 
               	} else { 
               		LOG("We have a Thermostat based Sensor! it=${sensor}", 4, null, "trace")
               		sensorDNI = "ecobee_sensor_thermostat-"+ it?.id + "-" + it?.name
				}
				LOG("sensorDNI == ${sensorDNI}", 4)
            	                
				def temperature = ""
				def occupancy = ""
                            
				it.capability.each { cap ->
					if (cap.type == "temperature") {
                   		LOG("updateSensorData() - Sensor (DNI: ${sensorDNI}) temp is ${cap.value}", 4)
                       	if ( cap.value.isNumber() ) { // Handles the case when the sensor is offline, which would return "unknown"
							temperature = cap.value as Double
							temperature = (temperature / 10).toDouble().round(settings.tempDecimals.toInteger()) // wantMetric() ? (temperature / 10).toDouble().round(1) : (temperature / 10).toDouble().round(1)
                       	} else if (cap.value == "unknown") {
                       		// TODO: Do something here to mark the sensor as offline?
                           	LOG("updateSensorData() - sensor (DNI: ${sensorDNI}) returned unknown temp value. Perhaps it is unreachable.", 1, null, "warn")
                           	// Need to mark as offline somehow
                           	temperature = "unknown"   
                       	} else {
                       		LOG("updateSensorData() - sensor (DNI: ${sensorDNI}) returned ${cap.value}.", 1, null, "error")
                       	}
					} else if (cap.type == "occupancy") {
						if(cap.value == "true") {
							occupancy = "active"
						} else if (cap.value == "unknown") {
                       		// Need to mark as offline somehow
                           	LOG("Setting sensor occupancy to unknown", 2, null, "warn")
                           	occupancy = "unknown"
                       	} else {
							occupancy = "inactive"
						}                            
					}
				}
                                            				
				def sensorData = [ decimalPrecision: tempDecimals ]
				sensorData << [
					temperature: ((temperature == "unknown") ? "unknown" : myConvertTemperatureIfNeeded(temperature, "F", tempDecimals.toInteger()))					
                ]
               	if (occupancy != "") {
               		sensorData << [ motion: occupancy ]
               	}
				sensorCollector[sensorDNI] = [data:sensorData]
                LOG("sensorCollector being updated with sensorData: ${sensorData}", 4)
			} // end sensor type check if
		} // End [tid] sensors loop
	} // End thermostats loop
	atomicState.remoteSensorsData = sensorCollector
	LOG("updateSensorData(): found these remoteSensors: ${sensorCollector}", 4)                
}

def updateThermostatData() {
	atomicState.timeOfDay = getTimeOfDay()
	def runtimeUpdated = atomicState.runtimeUpdated
    def thermostatUpdated = atomicState.thermostatUpdated
	def usingMetric = wantMetric() // cache the value to save the function calls
	def forcePoll = atomicState.forcePoll
    
	// Create the list of thermostats and related data
	// def i = 0
	
	atomicState.thermostats = atomicState.thermostatData.thermostatList.inject([:]) { collector, stat ->
		def dni = [ app.id, stat.identifier ].join('.')
        
		// we use atomicState.thermostatData because it holds the latest Ecobee API response, from which we can determine which stats actually
        // had updated data. Thus the following work is done ONLY for tstats that have updated data
		def tid = stat.identifier

		LOG("Updating dni $dni", 4)

    // Handle things that only change when runtime object is updated)
        def occupancy = "not supported"
        def tempTemperature
        def tempHeatingSetpoint
        def tempCoolingSetpoint
        def tempWeatherTemperature
		
        if (forcePoll || runtimeUpdated) {
			// Occupancy (motion)
        	// TODO: Put a wrapper here based on the thermostat brand
        	def thermSensor = atomicState.remoteSensors[tid].find { it.type == "thermostat" }
        
        	if(!thermSensor) {
				LOG("This particular thermostat does not have a built in remote sensor", 4)
				atomicState.hasInternalSensors = false
        	} else {
        		atomicState.hasInternalSensors = true
				LOG("updateThermostatData() - thermSensor == ${thermSensor}", 4 )
        
				def occupancyCap = thermSensor?.capability.find { it.type == "occupancy" }
				LOG("updateThermostatData() - occupancyCap = ${occupancyCap} value = ${occupancyCap.value}", 4, "", "info")
        
				// Check to see if there is even a value, not all types have a sensor
				occupancy =  occupancyCap.value ?: "not supported"
        	}
			if (atomicState.hasInternalSensors) { occupancy = (occupancy == "true") ? "active" : "inactive" }
			
			// Temperatures
			tempTemperature = myConvertTemperatureIfNeeded( (atomicState.runtime[tid].actualTemperature.toDouble() / 10), "F", settings.tempDecimals.toInteger() /* (usingMetric ? 1 : 1) */)
        	tempHeatingSetpoint = myConvertTemperatureIfNeeded( (atomicState.runtime[tid].desiredHeat.toDouble() / 10), "F", settings.tempDecimals.toInteger())
        	tempCoolingSetpoint = myConvertTemperatureIfNeeded( (atomicState.runtime[tid].desiredCool.toDouble() / 10), "F", settings.tempDecimals.toInteger())
        	tempWeatherTemperature = myConvertTemperatureIfNeeded( ((atomicState.weather[tid].forecasts[0].temperature.toDouble() / 10)), "F", settings.tempDecimals.toInteger())
        }
        
	// handle[tid] things that only change when the thermostat object is updated
		def heatHigh
		def heatLow
		def coolHigh
		def coolLow
		def heatRange
		def coolRange
		
		def hasHeatPump
		def hasForcedAir
		def hasElectric
		def hasBoiler
		def auxHeatMode

		if (forcePoll || thermostatUpdated) {
			// RANGES
			// UI works better with the same ranges for both heat and cool...
			// but the device handler isn't using these values for the UI right now (can't dynamically define the range)
			heatRange = usingMetric ? "(5..35)" : "(45..95)" 	// "(5..25)" : "(40..80)"
			coolRange = usingMetric ? "(5..35)" : "(45..95)" 	// "(18..35)" : "(65..95)"
			
			heatHigh = (atomicState.settings[tid].heatRangeHigh.toDouble() / 10.0).round(settings.tempDecimals.toInteger())
			heatLow = (atomicState.settings[tid].heatRangeLow.toDouble() / 10.0).round(settings.tempDecimals.toInteger())
			coolHigh = (atomicState.settings[tid].coolRangeHigh.toDouble() / 10.0).round(settings.tempDecimals.toInteger())
			coolLow = (atomicState.settings[tid].coolRangeLow.toDouble() / 10.0).round(settings.tempDecimals.toInteger())
			
			// calculate these anyway (for now) - it's easier to read the range while debugging
			if (heatLow && heatHigh) heatRange = "(${Math.round(heatLow)}..${Math.round(heatHigh)})"
			if (coolLow && coolHigh) coolRange = "(${Math.round(coolLow)}..${Math.round(heatHigh)})"
			
			// EQUIPMENT SPECIFICS
			hasHeatPump = atomicState.settings[tid].hasHeatPump
			hasForcedAir = atomicState.settings[tid].hasForcedAir
			hasElectric = atomicState.settings[tid].hasElectric
			hasBoiler = atomicState.settings[tid].hasBoiler
			auxHeatMode = (hasHeatPump) && (hasForcedAir || hasElectric || hasBoiler) // auxHeat = emergencyHeat if using a heatPump
		}
 
	// handle things that depend on both thermostat and runtime objects
		// EVENTS
		// Determine if an Event is running, find the first running event (only changes when thermostat object is updated)
    	def runningEvent = null
        def currentClimateName = ""
		def currentClimateId = ""
        def currentClimate = ""
        def currentFanMode = ""
		
		// what program is supposed to be running now?
		def scheduledClimateId = atomicState.program[tid].currentClimateRef
		def scheduledClimateName = ""
		if (scheduledClimateId) { 
            	scheduledClimateName = (atomicState.program[tid].climates.find { it.climateRef == scheduledClimateId }).name
		}
		LOG( "scheduledClimateId: ${scheduledClimateId}, scheduledClimateName: ${scheduledClimateName}", 4)
		// check which program is actually running now
		if ( atomicState.events[tid].size() > 0 ) {         
        	runningEvent = atomicState.events[tid].find { 
            	LOG("Checking event: ${it}", 5) 
                it.running == true
            }        	
		}
		def thermostatHold = ""
        if (runningEvent) {
			thermostatHold = runningEvent.type
            LOG("Found a running Event: ${runningEvent}", 3) 
            def tempClimateRef = runningEvent.holdClimateRef ?: ""
        	if ( runningEvent.type == "hold" ) {
				currentClimate = (tempClimateRef ? (atomicState.program[tid].climates.find { it.climateRef == tempClimateRef }).name : "")
               	currentClimateName = "Hold: " + currentClimate
			} else if (runningEvent.type == "vacation" ) {
               	currentClimateName = "Vacation"
            } else if (runningEvent.type == "quickSave" ) {
               	currentClimateName = "Quick Save"                
            } else if (runningEvent.type == "autoAway" ) {
             	currentClimateName = "Auto Away"
            } else if (runningEvent.type == "autoHome" ) {
               	currentClimateName = "Auto Home"
            } else {                
               	currentClimateName = runningEvent.type
            }
            // currentClimateId = runningEvent.holdClimateRef
			currentClimateId = tempClimateRef
		} else {
			if (scheduledClimateId) {
        		currentClimateId = scheduledClimateId
        		currentClimateName = scheduledClimateName
				currentClimate = scheduledClimateName
			} else {
        		LOG("updateThermostatData() - No climateRef or running Event was found", 1, null, "info")
            	currentClimateName = ""
        		currentClimateId = ""        	
        	}
		}
        LOG("updateThermostatData() - currentClimateName set = ${currentClimateName}  currentClimateId set = ${currentClimateId}", 4, null, "info")
		
        if (runningEvent) {
        	currentFanMode = atomicState.circulateFanModeOn ? "circulate" : atomicState.offFanModeOn ? "off" : runningEvent.fan
        } else {
        	currentFanMode = atomicState.runtime[tid].desiredFanMode
		}
		
		// HUMIDITY
		def humiditySetpoint = 0
        def humidity = atomicState.runtime[tid].desiredHumidity
        def dehumidity = atomicState.runtime[tid].desiredDehumidity
        def hasHumidifier = atomicState.settings[tid].hasHumidifier
        def hasDehumidifier = atomicState.settings[tid].hasDehumidifier || atomicState.settings[tid].dehumidifyWithAC // we can hide the details from the device handler
        def statMode = atomicState.settings[tid].hvacMode
		
		switch (statMode) {
			case 'heat':
				if (hasHumidifier) humiditySetpoint = humidity
				break;
			case 'cool':
            	if (hasDehumidifier) humiditySetpoint = dehumidity
				break;
			case 'auto':
			if ( hasHumidifier && hasDehumidifier) { humiditySetpoint = "${humidity}-${dehumidity}" }
                else if (hasHumidifier) { humiditySetpoint = humidity }
                else if (hasDehumidifier) { humiditySetpoint = dehumidity }
				break;
		}

		// EQUIPMENT STATUS
		def heatStages = atomicState.settings[tid].heatStages
		def coolStages = atomicState.settings[tid].coolStages 
		
		def equipStatus = atomicState.equipmentStatus[tid]
		def equipOpStat
        def thermOpStat 
		if (equipStatus.size() == 0) {
			equipStatus = 'idle'
			equipOpStat = equipStatus
            thermOpStat = equipStatus
		} else if (equipStatus == 'fan') {
			equipOpStat = 'fan only'
            thermOpStat = equipOpStat
		} else if (equipStatus.contains('eat')) {					// heating
        	thermOpStat = 'heating'
			if 		(equipStatus.contains('eat1')) { equipOpStat = (auxHeatMode) ? 'emergency' : (heatStages > 1) ? 'heat 1' : 'heating' }
			else if (equipStatus.contains('eat2')) { equipOpStat = 'heat 2' }
			else if (equipStatus.contains('eat3')) { equipOpStat = 'heat 3' }
			else if (equipStatus.contains('ump2')) { equipOpStat = 'heat pump 2' }
			else if (equipStatus.contains('ump3')) { equipOpStat = 'heat pump 3' }
			else if (equipStatus.contains('ump')) { equipOpStat = 'heat pump' }
			if (equipStatus.contains('humid')) { equipOpStat += ' hum' }	// humidifying if heat
		} else if (equipStatus.contains('ool')) {				// cooling
        	thermOpStat = 'cooling'
			if 		(equipStatus.contains('ool1')) { equipOpStat = (coolStages == 1) ? 'cooling' : 'cool 1' }
			else if (equipStatus.contains('ool2')) { equipOpStat = 'cool 2' }
			if (equipStatus.contains('dehum')) { equipOpStat += ' hum' }	// dehumidifying if cool
		} /* else if (equipStatus.contains('dehumid')) { // These can also run independent of heat/cool
        	equipOpStat = 'dehumidifier' 
        } else if (equipStatus.contains('humid') { 
        	equipOpStat = 'humidifier' 
        } // also: economizer, ventilator, compHotWater, auxHotWater
        */
											
		// Update the API link state and the lastPoll data. If we aren't running at a high debugLevel >= 4, then supply simple
		// poll status instead of the date/time (this simplifies the UI presentation, and reduces the chatter in the devices'
		// message log
        def apiConnection = apiConnected()
        def lastPoll = (debugLevel(4)) ? atomicState.lastPollDate : (apiConnection=='full') ? "Succeeded" : (apiConnection=='warn') ? "Incomplete" : "Failed"
        
		// we always send these because they change independent of the ecobee API objects
        def data = [
           	decimalPrecision: settings.tempDecimals,
			temperatureScale: getTemperatureScale(),
			lastPoll: lastPoll,
			apiConnected: apiConnection,
        	timeOfDay: atomicState.timeOfDay,
			debugLevel: settings.debugLevel.toInteger()
        ]
            
        if (forcePoll || atomicState.thermostatUpdated) {	// new settings, programs or events
			data += [
				coolMode: (atomicState.settings[tid].coolStages > 0),
            	coolStages: coolStages,
				heatMode: (atomicState.settings[tid].heatStages > 0),
            	heatStages: heatStages,
				autoMode: atomicState.settings[tid].autoHeatCoolFeatureEnabled,
                thermostatMode: statMode,
            	heatRangeHigh: heatHigh,
            	heatRangeLow: heatLow,
            	coolRangeHigh: coolHigh,
            	coolRangeLow: coolLow,
				heatRange: heatRange,
				coolRange: coolRange,
				currentProgramName: currentClimateName,
				currentProgramId: currentClimateId,
				currentProgram: currentClimate,
				scheduledProgramName: scheduledClimateName,
				scheduledProgramId: scheduledClimateId,
				scheduledProgram: scheduledClimateName,
				thermostatHold: thermostatHold,
            	hasHeatPump: hasHeatPump,
            	hasForcedAir: hasForcedAir,
            	hasElectric: hasElectric,
            	hasBoiler: hasBoiler,
				auxHeatMode: auxHeatMode,
            	hasHumidifier: hasHumidifier,
				hasDehumidifier: hasDehumidifier
			]
		}
		
		if (forcedPoll || atomicState.runtimeUpdated) {
			data += [            
				temperature:  String.format("%.${settings.tempDecimals}f", tempTemperature),  // usingMetric ? tempTemperature : tempTemperature.round(1) /*.toInteger()*/,
				heatingSetpoint: String.format("%.${settings.tempDecimals}f", tempHeatingSetpoint), //usingMetric ? tempHeatingSetpoint : tempHeatingSetpoint.round(1) /*.toInteger()*/,
				coolingSetpoint: String.format("%.${settings.tempDecimals}f", tempCoolingSetpoint), //usingMetric ? tempCoolingSetpoint : tempCoolingSetpoint.round(1) /*.toInteger()*/,
				thermostatFanMode: currentFanMode,
				humidity: atomicState.runtime[tid].actualHumidity,
				humiditySetpoint: humiditySetpoint,
				motion: occupancy,
				equipmentStatus: equipStatus,					// so that we can detect Heat/Cool 1 & 2 and aux equipment
				thermostatOperatingState: thermOpStat,			// getThermostatOperatingState(equipStatus)
				equipmentOperatingState: equipOpStat,
				weatherSymbol: atomicState.weather[tid].forecasts[0].weatherSymbol.toString(),
				weatherTemperature: String.format("%.${settings.tempDecimals}f", tempWeatherTemperature), //usingMetric ? tempWeatherTemperature : tempWeatherTemperature.round(1) /*.toInteger()*/	
			]
		}
		LOG("Event Data (${tid}) = ${data}", 4)

		collector[dni] = [thermostatId:tid, data:data /*,climateData:climateData */]
		// i++
		return collector
	}			
}

// translate Ecobee equipmentStatus into SmartThings thermostatOperatingState
// NOT USED ANY MORE
def getThermostatOperatingState(equipmentStatus) {
	def equipStatus = equipmentStatus.trim().toUpperCase()
    
    LOG("getThermostatOperatingState() - equipStatus == ${equipStatus}", 4)
    
	def currentOpState = equipStatus.contains('HEAT')? 'heating' : (equipStatus.contains('COOL')? 'cooling' : 
    	equipStatus.contains('FAN')? 'fan only': 'idle')
	return currentOpState
}

def getChildThermostatDeviceIdsString(singleStat = null) {
	if(!singleStat) {
    	LOG("getChildThermostatDeviceIdsString() - !singleStat returning the list for all thermostats", 4, null, "info")
		return thermostats.collect { it.split(/\./).last() }.join(',')
	} else {
    	// Only return the single thermostat
        LOG("Only have a single stat.", 4, singleStat, "debug")
        def ecobeeDevId = singleStat.device.deviceNetworkId.split(/\./).last()
        LOG("Received a single thermostat, returning the Ecobee Device ID as a String: ${ecobeeDevId}", 4, null, "info")
        return ecobeeDevId    	
    }
}

def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

private refreshAuthToken(child=null) {
	LOG("Entered refreshAuthToken()", 5)	

	// Update the timestamp for debugging purposes
	atomicState.lastTokenRefresh = now()
	atomicState.lastTokenRefreshDate = getTimestamp()    
    
	if(!atomicState.refreshToken) {    	
		LOG("refreshAuthToken() - There is no refreshToken stored! Unable to refresh OAuth token.", 1, child, "error")
    	apiLost("refreshAuthToken() - No refreshToken")
        return false
    } else {
		LOG("Performing a refreshAuthToken()", 4)
        
        def refreshParams = [
                method: 'POST',
                uri   : apiEndpoint,
                path  : "/token",
                query : [grant_type: 'refresh_token', code: "${atomicState.refreshToken}", client_id: smartThingsClientId],
        ]

        LOG("refreshParams = ${refreshParams}", 4)

		def jsonMap
        try {            
            httpPost(refreshParams) { resp ->
				LOG("Inside httpPost resp handling.", 3, child, "debug")
                if(resp.status == 200) {
                    LOG("refreshAuthToken() - 200 Response received - Extracting info." )
                    atomicState.reAttempt = 0 
                    apiRestored()                    
                    generateEventLocalParams() // Update the connected state at the thermostat devices
                    
                    jsonMap = resp.data // Needed to work around strange bug that wasn't updating state when accessing resp.data directly
                    LOG("resp.data = ${resp.data} -- jsonMap is? ${jsonMap}", 4, child)

                    if(jsonMap) {
                        LOG("resp.data == ${resp.data}, jsonMap == ${jsonMap}", 4, child)
						
                        atomicState.refreshToken = jsonMap.refresh_token
                        
                        // TODO - Platform BUG: This was not updating the state values for some reason if we use resp.data directly??? 
                        // 		  Workaround using jsonMap for authToken                       
                        LOG("atomicState.authToken before: ${atomicState.authToken}", 4, child)
                        def oldAuthToken = atomicState.authToken
                        atomicState.authToken = jsonMap?.access_token  
						LOG("atomicState.authToken after: ${atomicState.authToken}", 4, child)
                        if (oldAuthToken == atomicState.authToken) { 
                        	LOG("WARN: atomicState.authToken did NOT update properly! This is likely a transient problem.", 1, child, "warn")
						}

                        
                        // Save the expiry time for debugging purposes
                        LOG("Expires in ${resp?.data?.expires_in} seconds", 3, child)
                        atomicState.authTokenExpires = (resp?.data?.expires_in * 1000) + now()
                        LOG("Updated state.authTokenExpires = ${atomicState.authTokenExpires}", 4, child, "trace")

						LOG("Refresh Token = state =${atomicState.refreshToken}  == in: ${resp?.data?.refresh_token}", 4, child)
                        LOG("OAUTH Token = state ${atomicState.authToken} == in: ${resp?.data?.access_token}", 4, child)
                        

                        if(atomicState.action && atomicState.action != "") {
                            LOG("Token refreshed. Executing next action: ${atomicState.action}", 3, child)
                            "${atomicState.action}"()

                            // Reset saved action
                            atomicState.action = ""
                            if ( canSchedule() ) runIn(15, "pollChildren")
                        }

                    } else {
                    	LOG("No jsonMap??? ${jsonMap}", 2, child)
                    }
                    
                    return true
                } else {
                    LOG("Refresh failed ${resp.status} : ${resp.status.code}!", 1, child, "error")
                    return false
                }
            }
        } catch (groovyx.net.http.HttpResponseException e) {
        	//LOG("refreshAuthToken() - HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}  response? data: ${e.getResponse()?.getData()}", 1, null, "error")
            LOG("refreshAuthToken() - HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}", 1, child, "error")
            if (e.statusCode != 401) {
            	runIn(atomicState.reAttemptInterval, "refreshAuthToken")
            } else if (e.statusCode == 401) {            
				atomicState.reAttempt = atomicState.reAttempt + 1
		        if (atomicState.reAttempt > 3) {                       	
    		    	apiLost("Too many retries (${atomicState.reAttempt - 1}) for token refresh.")        	    
            	    return false
		        } else {
    		    	LOG("Setting up runIn for refreshAuthToken", 4, child)
        			if ( canSchedule() ) {            			
                        runIn(atomicState.reAttemptInterval, "refreshAuthToken") 
					} else { 
    	        		LOG("Unable to schedule refreshAuthToken, running directly", 4, child)						
	        	    	refreshAuthToken(child) 
    	        	}
        		}
            }
            generateEventLocalParams() // Update the connected state at the thermostat devices
            return false
		} catch (java.util.concurrent.TimeoutException e) {
			LOG("refreshAuthToken(), TimeoutException: ${e}.", 1, child, "error")
			// Likely bad luck and network overload, move on and let it try again
            if(canSchedule()) { runIn(atomicState.reAttemptInterval, "refreshAuthToken") } else { refreshAuthToken() }            
            return false
        } catch (Exception e) {
        	LOG("refreshAuthToken(), General Exception: ${e}.", 1, child, "error")            
            /*
            atomicState.reAttempt = atomicState.reAttempt + 1
	        if (atomicState.reAttempt > 3) {                       	
   		    	apiLost("Too many retries (${atomicState.reAttempt - 1}) for token refresh.")        	    
           	    return false
	        } else {
       			if ( canSchedule() ) {
           			// atomicState.connected = "warn"
					runIn(atomicState.reAttemptInterval, "refreshAuthToken") 
				} else { 
   	        		LOG("Unable to schedule refreshAuthToken, running directly", 2, child, "warn")
					// atomicState.connected = "warn"
        	    	refreshAuthToken(child) 
   	        	}
       		} */
            return false
        }
    }
}

def resumeProgram(child, deviceId) {
	LOG("Entered resumeProgram for deviceID: ${deviceID}", 5, child)
	def result = true
    
    def previousFanMinOnTime = atomicState."previousFanMinOnTime${deviceId}"
    def currentFanMinOnTime = getFanMinOnTime(child)
    def previousHVACMode = atomicState."previousHVACMode${deviceId}"
    def currentHVACMode = getHVACMode(child)
    // def currentHoldType = child.currentValue("thermostatHold")		// TODO: If we are in a vacation hold, need to delete the vacation
    // theoretically, if currentHoldType == "", we can skip all of this...theoretically
    
    LOG("resumeProgram() - atomicState.previousHVACMode = ${previousHVACMode} current (${currentHVACMode})   atomicState.previousFanMinOnTime = ${previousFanMinOnTime} current (${currentFanMinOnTime})", 3, child)	
    if ((previousHVACMode != null) && (currentHVACMode != previousHVACMode)) {
    	// Need to reset the HVAC Mode back to the previous state
        if (currentHVACMode == "off") { atomicState.offFanModeOn = false }
        if (currentHVACMode == "circulate") { atomicState.circulateFanModeOn = false }
        
        LOG("getHVACMode(child) != atomicState.previousHVACMode${deviceId} (${previousHVACMode})", 5, child, "trace")
        result = setHVACMode(child, deviceId, previousHVACMode)       
    }
    
    if ((previousFanMinOnTime != null) && (currentFanMinOnTime != previousFanMinOnTime)) {
    	// Need to reset the fanMinOnTime back to the previous settings              
        
        LOG("getFanMinOnTime(child) != atomicState.previousFanMinOnTime${deviceId} (${previousFanMinOnTime})", 5, child, "trace")
        def fanResult = setFanMinOnTime(child, deviceId, previousFanMinOnTime)
        result = result && fanResult      
    }
        
    // 					   {"functions":[{"type":"resumeProgram"}],"selection":{"selectionType":"thermostats","selectionMatch":"YYY"}}
    def jsonRequestBody = '{"functions":[{"type":"resumeProgram"}],"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '","resumeAll":"true"}}'
	LOG("jsonRequestBody = ${jsonRequestBody}", 4, child)
    
	result = sendJson(jsonRequestBody) && result
    LOG("resumeProgram(child) with result ${result}", 3, child)
	
	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result
}

def setHVACMode(child, deviceId, mode) {
	LOG("setHVACMode(${mode})", 4, child)
    def thermostatSettings = ',"thermostat":{"settings":{"hvacMode":"'+mode+'"}}'
    def thermostatFunctions = ''
	def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"functions":['+thermostatFunctions+']'+thermostatSettings+'}'
	
    def result = sendJson(child, jsonRequestBody)
    LOG("setHVACMode(child) with result ${result}", 3, child)    

	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result
}

def setFanMinOnTime(child, deviceId, howLong) {
	LOG("setFanMinOnTime(${howLong})", 4, child)
    def thermostatSettings = ',"thermostat":{"settings":{"fanMinOnTime":'+howLong+'}}'
    def thermostatFunctions = ''
    def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"functions":['+thermostatFunctions+']'+thermostatSettings+'}'
	
    def result = sendJson(child, jsonRequestBody)
    LOG("setFanMinOnTime(child) with result ${result}", 3, child)    

	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result
}

def setHold(child, heating, cooling, deviceId, sendHoldType=null, fanMode="", extraParams=[]) {
	int h = (getTemperatureScale() == "C") ? (cToF(heating) * 10) : (heating * 10)
	int c = (getTemperatureScale() == "C") ? (cToF(cooling) * 10) : (cooling * 10)
    
	LOG("setHold(): setpoints____ - h: ${heating} - ${h}, c: ${cooling} - ${c}, setHoldType: ${sendHoldType}", 3, child)
    
	if (fanMode != "") { 
		tstatSettings << [fan:"${fanMode}"] 
	}
        
    if (extraParams != []) {
    	tstatSettings << extraParams
    }                
    
	def jsonRequestBody 
    if (sendHoldType) {
    	jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"functions":[{"type":"setHold","params":{"coolHoldTemp":"' + c + '","heatHoldTemp":"' + h + '","holdType":"' + sendHoldType + '"}}]}'
	} else {
    	jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"functions":[{"type":"setHold","params":{"coolHoldTemp":"' + c + '","heatHoldTemp":"' + h + '"}}]}'
    }
    //      			   {"selection":{"selectionType":"thermostats","selectionMatch":"XXX"},             "functions":[{"type":"setHold","params":{"coolHoldTemp":"730","heatHoldTemp":"510","holdType":"nextTransition"}}],}
	//def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '","includeRuntime":true},"functions": [{ "type": "setHold", "params": { "coolHoldTemp": '+c+',"heatHoldTemp": '+h+', "holdType": '+sendHoldType+' } } ]}'
    LOG("about to sendJson with jsonRequestBody (${jsonRequestBody}", 4, child)
    
	def result = sendJson(child, jsonRequestBody)
    LOG("setHold: heating: ${h}, cooling: ${c} with result ${result}", 3, child)
	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result
}

def setMode(child, mode, deviceId) {
	LOG("setMode() to ${mode} with DeviceId: ${deviceId}", 5, child)
        
	def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"thermostat":{"settings":{"hvacMode":"'+"${mode}"+'"}}}'
    //                     {"selection":{"selectionType":"thermostats","selectionMatch":"XXX"},             "thermostat":{"settings":{"hvacMode":"cool"}}}    
	LOG("Mode Request Body = ${jsonRequestBody}", 4, child)
    
	def result = sendJson(jsonRequestBody)
    LOG("setMode to ${mode} with result ${result}", 4, child)
	if (result) {
    	child.generateQuickEvent("thermostatMode", mode, 15)
    } else {
    	LOG("Unable to set new mode (${mode})", 1, child, "warn")
    }

	if (canSchedule()) runIn( 5, poll, [overwrite: true])
	return result
}

def setFanMode(child, fanMode, deviceId, sendHoldType=null) {
	LOG("setFanMode() to ${fanMode} with DeviceID: ${deviceId}", 5, child)    
        
    // These values are ignored anyway when setting the fan
    def h = child.device.currentValue("heatingSetpoint")
    def c = child.device.currentValue("coolingSetpoint")
    
    def holdType = sendHoldType ?: whatHoldType()
    
    // Per this thread: http://developer.ecobee.com/api/topics/qureies-related-to-setfan
    // def extraParams = [isTemperatureRelative: "false", isTemperatureAbsolute: "false"]
	def thermostatSettings = ''
    def thermostatFunctions = ''     
    if (fanMode == "circulate") {    	
    	fanMode = "auto"        
        LOG("fanMode == 'circulate'", 5, child, "trace")        
        // Add a minimum circulate time here
        // TODO: Need to capture the previous fanMinOnTime and return to that value when the mode is changed again?
        atomicState."previousFanMinOnTime${deviceId}" = getFanMinOnTime(child)
        atomicState."previousHVACMode${deviceId}" = getHVACMode(child)        
		atomicState.circulateFanModeOn = true
        atomicState.offFanModeOn = false
        
        thermostatSettings = ',"thermostat":{"settings":{"fanMinOnTime":15}}'
        thermostatFunctions = '{"type":"setHold","params":{"coolHoldTemp":"' + c + '","heatHoldTemp":"' + h + '","holdType":"' + holdType + '","fan":"'+fanMode+'","isTemperatureAbsolute":false,"isTemperatureRelative":false}}'
    } else if (fanMode == "off") {
    	// How to turn off the fan http://developer.ecobee.com/api/topics/how-to-turn-fan-off
        // NOTE: Once you turn it off it does not automatically come back on if you select resume program
        atomicState."previousFanMinOnTime${deviceId}" = getFanMinOnTime(child)
        atomicState."previousHVACMode${deviceId}" = getHVACMode(child)        
    	atomicState.circulateFanModeOn = false    
        atomicState.offFanModeOn = true
        fanMode = "auto"        
        thermostatSettings = ',"thermostat":{"settings":{"hvacMode":"off","fanMinOnTime":0}}'
        thermostatFunctions = ''
    } else {
		atomicState.circulateFanModeOn = false    
        atomicState.offFanModeOn = false
        thermostatSettings = ''
        thermostatFunctions = '{"type":"setHold","params":{"coolHoldTemp":"' + c + '","heatHoldTemp":"' + h + '","holdType":"' + holdType + '","fan":"'+fanMode+'","isTemperatureAbsolute":false,"isTemperatureRelative":false}}'
    }    
	
    // {"selection":{"selectionType":"thermostats","selectionMatch":"312989153500"},"functions":[{"type":"setHold","params":{"coolHoldTemp":"73","heatHoldTemp":"66","holdType":"nextTransition","fan":"circulate","isTemperatureAbsolute":false,"isTemperatureRelative":false}}],"thermostat":{"settings":{"fanMinOnTime":15}}}
	def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '"},"functions":['+thermostatFunctions+']'+thermostatSettings+'}'
    LOG("about to sendJson with jsonRequestBody (${jsonRequestBody}", 4, child)
    
	def result = sendJson(child, jsonRequestBody)
    LOG("setFanMode: heating: ${h}, cooling: ${c} with result ${result}", 3, child)
	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result    
}

def setProgram(child, program, deviceId, sendHoldType=null) {
	// NOTE: Will use only the first program if there are two with the same exact name
	LOG("setProgram() to ${program} with DeviceID: ${deviceId}", 3, child, "debug")    
    // def climateRef = program.toLowerCase()   
    
	// def therm = atomicState.thermostatData?.thermostatList?.find { it.identifier.toString() == deviceId.toString() }
	
/*	def index = -1
	for (i in 0..atomicState.thermostatData.thermostatList.size() - 1) {
		if (atomicState.thermostatData.thermostatList[i].identifier.toString() == deviceId.toString()) index = i
	}
	
	if (index < 0) {
		LOG("setProgram() Ooops! - can't find thermostat!!!", 3, child, "error")
		return false
	}
*/	
    def climates = atomicState.program[deviceId.toString()].climates
    def climate = climates?.find { it.name.toString() == program.toString() }
    LOG("climates - {$climates}", 5, child)
    LOG("climate - {$climate}", 5, child)
    def climateRef = climate?.climateRef.toString()
    
	LOG("setProgram() - climateRef = {$climateRef}", 3, child)
	
    if (climate == null) { return false }
    
	def jsonRequestBody
    if (sendHoldType) {
    	jsonRequestBody = '{"functions":[{"type":"setHold","params":{"holdClimateRef":"'+climateRef+'","holdType":"'+sendHoldType+'"}}],"selection":{"selectionType":"thermostats","selectionMatch":"'+deviceId+'"}}'
    } else {
    	jsonRequestBody = '{"functions":[{"type":"setHold","params":{"holdClimateRef":"'+climateRef+'"}}],"selection":{"selectionType":"thermostats","selectionMatch":"'+deviceId+'"}}'
    }
    // {"functions":[{"type":"setHold","params":{"holdClimateRef": "home","holdType":"nextTransition"}}],"selection":{"selectionType":"thermostats","selectionMatch":"312989153500"}}
	// {"functions":[{"type":"setHold","params":{"holdClimateRef":"sleep","holdType":"nextTransition"}}],"selection":{"selectionType":"thermostats","selectionMatch":"312989153500"}}	
    LOG("about to sendJson with jsonRequestBody (${jsonRequestBody}", 4, child)    
	def result = sendJson(child, jsonRequestBody)	
    LOG("setProgram with result ${result}", 3, child)
    dirtyPollData()
	if (canSchedule()) runIn( 5, poll, [overwrite: true])
    return result
}

// API Helper Functions
private def sendJson(child=null, String jsonBody) {
	// Reset the poll timer to allow for an immediate refresh
	dirtyPollData()
    
	def returnStatus = false
	def cmdParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${atomicState.authToken}"],
			body: jsonBody
	]
	
	try{
		httpPost(cmdParams) { resp ->
   	    	returnStatus = resp.data.status.code

			LOG("sendJson() resp.status ${resp.status}, resp.data: ${resp.data}, returnStatus: ${returnStatus}", 2, child)
				
           	// TODO: Perhaps add at least two tries incase the first one fails?
			if(resp.status == 200) {				
				LOG("Updated ${resp.data}", 4)
				returnStatus = resp.data.status.code
				if (resp.data.status.code == 0) {
					LOG("Successful call to ecobee API.", 2, child)
					apiRestored()
                    generateEventLocalParams()
				} else {
					LOG("Error return code = ${resp.data.status.code}", 1, child, "error")
				}
                // Reset saved state
                atomicState.savedActionJsonBody = null
        		atomicState.savedActionChild = null
			} else {
            	// Should never get here as a non-200 response is supposed to trigger an Exception
   	        	LOG("Sent Json & got http status ${resp.status} - ${resp.status.code}", 2, child, "warn")
			} // resp.status if/else
		} // HttpPost
	} catch (groovyx.net.http.HttpResponseException e) {
    	LOG("sendJson() - HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}  response? data: ${e.response.data.status.code}", 1, child, "error")	
		if (e.response.data.status.code == 14) {
	        // atomicState.connected = "warn"
    	    atomicState.savedActionJsonBody = jsonBody
        	atomicState.savedActionChild = child.deviceNetworkId
        	atomicState.action = "sendJsonRetry"
        	// generateEventLocalParams()
        	refreshAuthToken(child)
        } else {
        	LOG("Error posting json received error status code: ${e.response.data.status.code}", 2, child, "warn")        
        }
    } catch(Exception e) {
    	// Might need to further break down 
		LOG("sendJson() - Exception Sending Json: " + e, 1, child, "error")
        // atomicState.connected = "warn"
        // generateEventLocalParams()
	}

	if (returnStatus == 0)
		return true
	else
		return false
}

private def sendJsonRetry() {
	LOG("sendJsonRetry() called", 4)
    def child = null
    if (atomicState.savedActionChild) {
    	child = getChildDevice(atomicState.savedActionChild)
    }
    
    if (atomicState.savedActionJsonBody == null) {
    	LOG("sendJsonRetry() - no saved JSON Body to send!", 2, child, "warn")
        return false
    }    
    
    return sendJson(child, atomicState.savedActionJsonBody)
}

private def getChildThermostatName() { return "Ecobee Thermostat" }
private def getChildSensorName()     { return "Ecobee Sensor" }
private def getServerUrl()           { return "https://graph.api.smartthings.com" }
private def getShardUrl()            { return getApiServerUrl() }
private def getCallbackUrl()         { return "${serverUrl}/oauth/callback" }
private def getBuildRedirectUrl()    { return "${serverUrl}/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}&apiServerUrl=${shardUrl}" }
private def getApiEndpoint()         { return "https://api.ecobee.com" }

// This is the API Key from the Ecobee developer page. Can be provided by the app provider or use the appSettings
private def getSmartThingsClientId() { 
	if(!appSettings.clientId) {
		return "obvlTjUuuR2zKpHR6nZMxHWugoi5eVtS"		
	} else {
		return appSettings.clientId 
    }
}

private def LOG(message, level=3, child=null, logType="debug", event=false, displayEvent=true) {
	def prefix = ""
    def logTypes = ["error", "debug", "info", "trace", "warn"]

    if(!logTypes.contains(logType)) {
    	log.error "LOG() - Received logType ${logType} which is not in the list of allowed types."
        if (event && child) { debugEventFromParent(child, "LOG() - Received logType ${logType} which is not in the list of allowed types.") }
        logType = "debug"
    }
    
    if ( logType == "error" ) { 
    	atomicState.lastLOGerror = "${message} @ ${getTimestamp()}"
        atomicState.LastLOGerrorDate = getTimestamp()        
	}
    // if ( debugLevel(0) ) { return }
	if ( debugLevel(5) ) { prefix = "LOG: " }
	if ( debugLevel(level) ) { 
    	log."${logType}" "${prefix}${message}"        
        if (event) { debugEvent(message, displayEvent) }
        if (child) { debugEventFromParent(child, message) }
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

private def debugEventFromParent(child, message) {
	 def data = [
            	debugEventFromParent: message
            ]         
	if (child) { child.generateEvent(data) }
}

// TODO: Create a more generic push capability to send notifications
// send both push notification and mobile activity feeds
private def sendPushAndFeeds(notificationMessage) {
	LOG("sendPushAndFeeds >> notificationMessage: ${notificationMessage}", 1, null, "warn")
	LOG("sendPushAndFeeds >> atomicState.timeSendPush: ${atomicState.timeSendPush}", 1, null, "warn")
    
    def msg = "Your Ecobee thermostat(s) at ${location.name} " + notificationMessage		// for those that have multiple locations, tell them where we are
    if (atomicState.timeSendPush) {
        if ( (now() - state.timeSendPush) >= (1000 * 60 * 60 * 1)) { // notification is sent to remind user no more than once per hour
        	if (location.contactBookEnabled && settings.recipients) {
				sendNotificationToContacts(msg, settings.recipients, [event: true]) 
    		} else {
				sendPush(msg)
    		}
            sendActivityFeeds(notificationMessage)
            atomicState.timeSendPush = now()
        }
    } else {
        if (location.contactBookEnabled && settings.recipients) {
			sendNotificationToContacts(msg, settings.recipients, [event: true]) 
    	} else {
			sendPush(msg)
    	}
        sendActivityFeeds(notificationMessage)
        atomicState.timeSendPush = now()
    }
    // This is done in apiLost now
    // atomicState.authToken = null
}

private def sendActivityFeeds(notificationMessage) {
    def devices = getChildDevices()
    devices.each { child ->
        child.generateActivityFeedsEvent(notificationMessage) //parse received message from parent
    }
}



// Helper Functions
// Creating my own as it seems that the built-in version only works for a device, NOT a SmartApp
def myConvertTemperatureIfNeeded(scaledSensorValue, cmdScale, precision) {
	if ( (cmdScale != "C") && (cmdScale != "F") && (cmdScale != "dC") && (cmdScale != "dF") ) {
    	// We do not have a valid Scale input, throw a debug error into the logs and just return the passed in value
        LOG("Invalid temp scale used: ${cmdScale}", 2, null, "error")
        return scaledSensorValue
    }

	def returnSensorValue 
    
	// Normalize the input
	if (cmdScale == "dF") { cmdScale = "F" }
    if (cmdScale == "dC") { cmdScale = "C" }

	LOG("About to convert/scale temp: ${scaledSensorValue}", 5, null, "trace", false)
	if (cmdScale == getTemperatureScale() ) {
    	// The platform scale is the same as the current value scale
        returnSensorValue = scaledSensorValue.round(precision)
    } else if (cmdScale == "F") {		    	
    	returnSensorValue = fToC(scaledSensorValue).round(precision)
    } else {
    	returnSensorValue = cToF(scaledSensorValue).round(precision)
    }
    LOG("returnSensorValue == ${returnSensorValue}", 5, null, "trace", false)
    return returnSensorValue
}

def wantMetric() {
	return (getTemperatureScale() == "C")
}

private def cToF(temp) {
	LOG("cToF entered with ${temp}", 5, null, "info")
	return (temp * 1.8 + 32) as Double
    // return celsiusToFahrenheit(temp)
}
private def fToC(temp) {	
	LOG("fToC entered with ${temp}", 5, null, "info")
	return (temp - 32) / 1.8 as Double
    // return fahrenheitToCelsius(temp)
}


// Establish the minimum amount of time to wait to do another poll
private def  getMinMinBtwPolls() {
    // TODO: Make this configurable in the SmartApp
	return 1
}

private def getPollingInterval() {
	// return (settings.pollingInterval?.toInteger() >= 5) ? settings.pollingInterval.toInteger() : 5
    return settings.pollingInterval?.toInteger()
}

private def String getTimestamp() {
	// There seems to be some possibility that the timeZone will not be returned and will cause a NULL Pointer Exception
	def timeZone = location?.timeZone ?: ""
    // LOG("timeZone found as ${timeZone}", 5)
    if(timeZone == "") {
    	return new Date().format("yyyy-MM-dd HH:mm:ss z")
    } else {
		return new Date().format("yyyy-MM-dd HH:mm:ss z", timeZone)
	}
}

private def getTimeOfDay() {
	def nowTime 
    if(location.timeZone) {
    	nowTime = new Date().format("HHmm", location.timeZone).toDouble()
    } else {
    	nowTime = new Date().format("HHmm").toDouble()
    }
    LOG("getTimeOfDay() - nowTime = ${nowTime}", 4, null, "trace")
    if ( (nowTime < atomicState.sunriseTime) || (nowTime > atomicState.sunsetTime) ) {
    	return "night"
    } else {
    	return "day"
    }
}

// Are we connected with the Ecobee service?
private String apiConnected() {
	// values can be "full", "warn", "lost"
	if (atomicState.connected == null) atomicState.connected = "warn"
	return atomicState.connected?.toString() ?: "lost"
}


private def apiRestored() {
	atomicState.connected = "full"
	unschedule("notifyApiLost")
}

private def getDebugDump() {
	 def debugParams = [when:"${getTimestamp()}", whenEpic:"${now()}", 
				lastPollDate:"${atomicState.lastPollDate}", lastScheduledPollDate:"${atomicState.lastScheduledPollDate}", 
				lastScheduledWatchdogDate:"${atomicState.lastScheduledWatchdogDate}",
				lastTokenRefreshDate:"${atomicState.lastTokenRefreshDate}", 
                initializedEpic:"${atomicState.initializedEpic}", initializedDate:"${atomicState.initializedDate}",
                lastLOGerror:"${atomicState.lastLOGerror}", authTokenExpires:"${atomicState.authTokenExpires}"
			]    
	return debugParams
}

private def apiLost(where = "[where not specified]") {
    LOG("apiLost() - ${where}: Lost connection with APIs. unscheduling Polling and refreshAuthToken. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 1, null, "error")
    atomicState.apiLostDump = getDebugDump()
    if (apiConnected() == "lost") {
    	LOG("apiLost() - already in lost atomicState. Nothing else to do. (where= ${where})", 5, null, "trace")
        return
    }
   
    // provide cleanup steps when API Connection is lost
	def notificationMessage = "is disconnected from SmartThings/Ecobee, because the access credential changed or was lost. Please go to the Ecobee (Connect) SmartApp and re-enter your account login credentials."
    atomicState.connected = "lost"
    atomicState.authToken = null
    
    sendPushAndFeeds(notificationMessage)
	generateEventLocalParams()

    LOG("Unscheduling Polling and refreshAuthToken. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 0, null, "error")
    
    // Notify each child that we lost so it gets logged
    if ( debugLevel(3) ) {
    	def d = getChildDevices()
    	d?.each { oneChild ->
        	LOG("apiLost() - notifying each child: ${oneChild} of loss", 0, child, "error")
		}
    }
    
    unschedule("pollScheduled")
    unschedule("scheduleWatchdog")
    runEvery3Hours("notifyApiLost")
}

def notifyApiLost() {
	def notificationMessage = "is disconnected from SmartThings/Ecobee, because the access credential changed or was lost. Please go to the Ecobee (Connect) SmartApp and re-enter your account login credentials."
    if ( atomicState.connected == "lost" ) {
    	generateEventLocalParams()
		sendPushAndFeeds(notificationMessage)
        LOG("notifyApiLost() - API Connection Previously Lost. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 0, null, "error")
	} else {
    	// Must have restored connection
        unschedule("notifyApiLost")
    }    
}

private String childType(child) {
	// Determine child type (Thermostat or Remote Sensor)
    if ( child.hasCapability("Thermostat") ) { return getChildThermostatName() }
    if ( child.name.contains( getChildSensorName() ) ) { return getChildSensorName() }
    return "Unknown"
}

private getFanMinOnTime(child) {
	LOG("getFanMinOnTime() - Looking up current fanMinOnTime for ${child}", 4, child)
    def devId = getChildThermostatDeviceIdsString(child)
    LOG("getFanMinOnTime() Looking for ecobee thermostat ${devId}", 5, child, "trace")
    
/*    // def therm = atomicState.thermostatData?.thermostatList?.find { it.identifier.toString() == devId.toString() }
	def index = -1
	for (i in 0..atomicState.thermostatData.thermostatList.size() - 1) {
		if (atomicState.thermostatData.thermostatList[i].identifier.toString() == devId.toString()) index = i
	}
	
	if (index < 0) {
		LOG("getFanMinOnTime() Ooops! - can't find thermostat!!!", 3, child, "error")
		index = 0
	}
*/
    def fanMinOnTime = atomicState.settings[devId.toString()].fanMinOnTime
    LOG("getFanMinOnTime() fanMinOnTime is ${fanMinOnTime} for therm ${atomicState.thermostatData.thermostatList[index].identifier}", 4, child)
	return fanMinOnTime
}

private getHVACMode(child) {
	LOG("Looking up current hvacMode for ${child}", 4, child)
    def devId = getChildThermostatDeviceIdsString(child)
    LOG("getHVACMode() Looking for ecobee thermostat ${devId}", 5, child, "trace")
    
/*    //def therm = atomicState.thermostatData?.thermostatList?.find { it.identifier.toString() == devId.toString() }
	def index = -1
	for (i in 0..atomicState.thermostatData.thermostatList.size() - 1) {
		if (atomicState.thermostatData.thermostatList[i].identifier.toString() == devId.toString()) index = i
	}
	
	if (index < 0) {
		LOG("getHVACMode() Ooops! - can't find thermostat!!!", 3, child, "error")
		index = 0
	}
    */
    def hvacMode = atomicState.settings[devId.toString()].hvacMode		// FIXME
	LOG("getHVACMode() hvacMode is ${hvacMode} for therm ${atomicState.thermostatData.thermostatList[index].identifier}", 4, child)
	return hvacMode
}

/*
private getThermostatHoldType(child) {
	LOG("Looking up current hold Type for ${child}", 4, child)
    def devId = getChildThermostatDeviceIdsString(child)
    LOG("getThermostatHoldType() Looking for ecobee thermostat ${devId}", 5, child, "trace")
    
	def index = -1
	for (i in 0..atomicState.thermostatData.thermostatList.size() - 1) {
		if (atomicState.thermostatData.thermostatList[i].identifier.toString() == devId.toString()) index = i
	}
	
	if (index < 0) {
		LOG("getthermostatHoldType() Ooops! - can't find thermostat!!!", 3, child, "error")
		index = 0
	}

    def holdMode = atomicState.settings[devId.toString()].thermostatHold		// FIXME
	LOG("getThermostatHoldType() hvacMode is ${hvacMode} for therm ${atomicState.thermostatData.thermostatList[index].identifier}", 4, child)
	return hvacMode
}
*/

def getAvailablePrograms(thermostat) {
	// TODO: Finish implementing this!
    LOG("Looking up the available Programs for this thermostat (${thermostat})", 4)
    def devId = getChildThermostatDeviceIdsString(thermostat)
    LOG("getAvailablePrograms() Looking for ecobee thermostat ${devId}", 5, thermostat, "trace")
    
/*    // def therm = atomicState.thermostatData?.thermostatList?.find { it.identifier.toString() == devId.toString() }
	def index = -1
	for (i in 0..atomicState.thermostatData.thermostatList.size() - 1) {
		if (atomicState.thermostatData.thermostatList[i].identifier.toString() == devId.toString()) index = i
	}
	
	if (index < 0) {
		LOG("getAvailablePrograms() Ooops! - can't find thermostat!!!", 3, child, "error")
		index = 0
	}
 */
    def climates = atomicState.program[devId.toString()].climates
    
    return climates?.collect { it.name }
}

private def whatHoldType() {
	def sendHoldType = settings.holdType ? (settings.holdType=="Temporary" || settings.holdType=="Until Next Program")? "nextTransition" : (settings.holdType=="Permanent" || settings.holdType=="Until I Change")? "indefinite" : "indefinite" : "indefinite"
	LOG("Entered whatHoldType() with ${sendHoldType}  settings.holdType == ${settings.holdType}")
	 
    return sendHoldType
}

private debugLevel(level=3) {
	// log.trace("debugLevel() -- settings.debugLevel == ${settings.debugLevel}")
    if(settings.debugLevel == "0") { 
    	// log.trace("debugLevel() - debugLvlNum == 0 triggered")
    	return false 
	}
    
	def debugLvlNum = settings.debugLevel?.toInteger() ?: 3
    def wantedLvl = level?.toInteger()    
    // log.trace("debugLvlNum = ${debugLvlNum}; wantedLvl = ${wantedLvl}")
	return ( debugLvlNum >= wantedLvl )
    
}

// Mark the poll data as "dirty" to allow a new API call to take place
private def dirtyPollData() {
	LOG("dirtyPollData() called to reset poll state", 5)
	atomicState.forcePoll = true
    atomicState.lastRevisions = "foo"
    atomicState.latestRevisions = "bar"
	atomicState.lastEquipStatus = null
	atomicState.lastEquipOpStat = null
}
