Ecobee support for SmartThings - Enhanced
=========================================
This updated and enhanced version of the Ecobee thermostat support for SmartThings is based off of the Open Source enhancements developed 
and contributed by Sean Kendall Schneyer (@StrykerSKS), which was in turn based upon the original Open Source device driver provided by 
SmartThings.

I hereby contribute my edits and additions to the same Open Source domain as the prior works noted above. (Open Source descriptions here). 
I attest that my edits and modifications are solely my own original work, and not derived, copied or replicated from other non-open source 
works. Any similarity to other works is therefore a product of development using the Ecobee API and the SmartThings development 
environment, which independently and together define a narrow band of operational leeway in order to successfully interface with the two 
environments.\

This work is entirely FREE - users are under no obligation to compensate me for my contributions to this Open Source project. However, if 
you would like to make a small donation, you can do so here:

https://paypal.me/BarryABurke

<b>IMPORTANT!</b> Before installing and using my enhanced version, I <i>strongly encourage</i> you to review the most excellent 
documentation written by @StrykerSKS for his updates. You can find it here:

https://github.com/StrykerSKS/SmartThingsPublic/blob/StrykerSKS-Ecobee3/smartapps/smartthings/ecobee-connect.src/README.md

<b>ALSO IMPORTANT! Please review the INSTALL document before attempting to install this enhanced ecobee support</b>

Notable Enhancements and Changes
--------------------------------
This work represents a significant overhaul of the aforemention prior implementations. Most notable of these include:
* <b>Efficiency and Performance</b>
  * A <i>significant</i> reduction in the frequency and amount of data collected from the Ecobee cloud servers 
  * A <i>significant</i> reduction in the amount of data sent from the Ecobee (connect) SmartApp to the individual ecobee thermostat 
    sensor device(s) 
* <b>User Interface Enhancements</b>
  * Thermostat devices
    * Displays heating or cooling stage indicator (when unit has more than 1 stage of either)
    * Displays when humidification and dehumidification is active (including when configured to use the A/C to dehumidify)
    * Displays when operating in Smart Recovery for both Heating and Cooling (heating/cooling in advance of a Program change)
    * Displays when thermostat is Offline (i.e., has lost connectivity with the Ecobee Cloud due to power or network outage)
    * New filled icons used to indicate at a glance when a Program is in Hold
    * A new Hold Status field displays when the current Hold or Vacation will end; this is also to display when the last update was 
      received if the thermostat is currently Offline
    * Color icons and background of the main tile utilize the ecobee 3 thermostat color palette
    * Can now cancel an active Vacation
    * Vacation uses a new Airplane icon
    * When heating/cooling, displays the target temperature
    * when idle, displays the temperature at which a call for heating/cooling demand will be made
  * Sensor devices
    * A new multiAttributeTile replaces the old presentation, with motion displayed at the bottom left corner
    * Now displays the Thermostat's current Program within each Sensor device
    * New mini-icons indicate which of the 3 default programs (Home, Away, Sleep) the sensor is included in. 
      The sensor can be added or removed from a Program by tapping these mini icons.
    * Includes 4 new "blank" mini-tiles that are utilized by the new Smart Room Helper App (see below)
  * All devices
    * Now have the ability to display 0, 1 or 2 decimal places of precision for thermostats and sensors. The default for metric locales is 
      1 decimal place (e.g. 25.2°) and for imperial locales is 0 (e.g., 71°). These defaults can be overridded in the preferences section 
      Ecobee (Connect) SmartApp, and changes take effect from that point on.
    * The "Recently" device notifications log has been carefully optimized to show color icons and concise messages for all device 
      updates.
    
* <b>Operational Enhancements</b>
  * Resume vs Program change: If a request is made to change the thermostat's Program (<i>aka</i> Climate) to the currently scheduled 
    Program, and the request type is Temporary (<i>aka</i> nextTransition), a Resume is executed instead of setting a new Hold. If a 
    Permanent hold is requested, it is effected directly, and no attempt to resume is made.
  * <code>thermostat.resumeProgram</code> now defaults to <i>always</i> request that ALL outstanding program holds are removed, not 
    just the most recent. 
    * NOTE: This is how the new ecobee3 thermostats operate via their UI and manually. Most Ecobee thermostats support the notion of 
      "stacked" Holds, where each request to change temp/program would "stack" a new Hold on the old, and it would require multiple 
      successive Resume program calls to reset the stack. For simplicity, this version always requests that ALL hold events be cleared 
      on a rresume program request.
    * NOTE: It is still possible programmatically to request that only the most recent hold request is resumed bu calling 
      <code>thermostat.resumeProgram(false)</code>. The new Smart Zone helper app utilizes this to return from its temporary fanOn 
      events. 
   * Polling Frequency: As a result of the aforementioned operational efficiency enhancements, it is now possible to run ecobee 
     thermostat devices with very short polling frequency - as low as once per minute. Although Ecobee documents that none of the 
     API's data objects are updated more frequently than every 3 minutes, this has been observed to not be true. Many operations
     will cause the thermostat to be immediately updated, such as when resuming a program or cancelling an active vacation. 
      * NOTE: Your mileage may vary - ecobee does in fact recommend <i><u>a minimum of 3 minutes</u></i>, and they may choose to prohibit 
        shorter polling frequencies at any time.
      * It is now possible to select a 2 minute polling frequency, which is perhaps a happy medium between the Ecobee-recommended 3 
        minute minimum poll time, and the 1 minute frequency used primarily for debugging.
  * Watchdog Devices: A practical alternative to short polling frequency is now to configure one or more "watchdog" devices. In addition 
    to ensuring that the scheduled polls haven't gotten derailed by the SmartThings infrastructure, an event from, say, a temperature 
    change on a SmartThings MultiSensor will also cause a poll of the ecobee API to check if anything has changed. If not, no foul - the 
    "heavy" API call is avoided.
  * Debug Level: The default debug level is still 3 (defined in the Ecobee (Connect) Smart App, in the Preferences section). Setting this 
    to <b>2</b> will present a minimalist but informative subset of messages to Live Logging for all of the related devices and Smart 
    Apps.

* <b>Helper SmartApps</b> 
  <p>Simplified the display list of current and optional Helper Apps to reduce scrolling and improve clarity</p>
  
  * <b>Mode/Routines</b> handler: New "smart" changes to thermostat program:
    * Send a notification to the location's notification log explaining what was done
    * If current program is already the requested program, and we aren't in a hold, then leave it alone
    * If thermostat currently in "hold" mode, and the originally scheduled program is the same as the target, then simply 
     <code>resumeProgram(resumeAll=true)</code>
    * New capability allows thermostat Program changes to invoke Mode Changes or to execute a Routine. E.g., when the thermostat changes 
      to 
      Vacation mode, run the "Goodbye!" Routine
      
  * New <b>Smart Circulation</b> Handler: mimics the latent (not yet enabled) ecobee3 function of the same name. Monitors the temperature 
    across 2 or more sensors (not necessarily ecobee sensors - ANY ST thermometer will do), and if the temperature delta between the 
    highest and lowest reading is greater than a configurable range, increases the minimum fan on time (automated circulation). Reduces 
    the fanMinOnTime again when min/max temperatures are within 1F/0.5C of each other. Minimum and maximum fan circulation time (minutes 
    per hour) and frequency of updating are also configurable.
    
  * New <b>Smart Room</b> Handler: for rooms that are frequently not used, automates adding/removing the Ecobee Sensor to specified 
    Programs and optionally opens/closes SmartThings-controlled vents (e.g. EcoNet and Keen vents). A "Smart Room" can be automatically 
    enabled by opening the door for a specified time, and disabled after the door has been closed for a specified period. Optional Windows 
    will pause the "active" state while open and re-enable it once they are closed. Door, Window, Vent and SmartRoom status are displayed 
    in a row of mini-icons within the device.
    
  * New <b>Smart Zone</b> Handler: attempts to have ALL zones on a single HVAC synchronize their circulation schedule, so that the HVAC 
    fan isn't run independently for each zone (thereby reducing electricity demand). 

* <b>Other Miscellaneous Enhancements</b>
  * SmartApp Name: it is now possible to rename each instance of the Ecobee (Connect) SmartApp. This is useful for those with multiple 
    locations/hubs.
  * Contact list support: For those who have enabled SmartThings contact lists, you can now select which users will recieve Push 
    notifications of major issues (e.g., warnings about API connection loss). If the contact list is not enabled, a Push message is 
    sent to all users (as before)
  * The Push message will include the location name in the message (again, for users with multiple locations)
  * Thermostat attributes: Virtually ALL of the significant thermostat attributes provided by the Ecobee API are now reflected in the 
    thermostat device as Attributes. This allows SmartThings developers to query more detail about a specific thermostat without having to 
    interface to the API directly. The accessible Attributes (and the current values) appear in the device report for each thermostat; 
    these can be accessed programatically as <code><i>deviceId</i>.currentValue("<i>AttributeName</i>")</code>
  * Added numerous new command entry points so that programmers and CoRE users can automate more ecobee operations, included add/delete 
    sensor from a program, cancel a running Vacation,and more
  
* <b>Bug Fixes</b>
  * All decimal values are now mathematically rounded, such that 72.4° displays as 72°, and 72.5° 
    displays as 73° (if decimal precision is set to 0).
  * The prior version defined a "sleep()" command, and allowed you to request Sleep mode via button press in the UI - these never worked 
    (in fact, silently failed) because it is not possible to overload the built-in sleep() function of the SmartThings/Groovy/Amazon S3 
    environment. Thus, the sleep() call has been replaced with two new calls: asleep() and night(), and the UI now properly supports 
    changing to sleep() mode.
 
Notes, Warning and Caveats
--------------------------
* This work is provided as-is, with no assurance of effectiveness or warranty of any kind, including no protections against defects or 
  damage. <b><i>Use at your own risk.</i></b>
* Using polling frequencies shorter than 3 minutes is at the user's risk - this may or may not work, and ecobee may choose to inhibit this
  at any time, without notice.  <b><i>Use at your own risk.</i></b>
* The Celsius support has NOT been tested, and may well have been broken. If so, let me know and I'll try to fix it.  <b><i>Use at your 
  own risk.</i></b>
* That said, the Fahrenheit support has not been tested exhausitvely either (I can't test cooling mode right now, in the dead of New 
  England winter, for example).  <b><i>Use at your own risk.</i></b>
* Oh, and  <b><i>Use at your own risk.</i></b>
