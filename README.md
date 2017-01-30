Ecobee support for SmartThings - Enhanced
=========================================
This updated and enhanced version of the Ecobee thermostat support for SmartThings is based off of the Open Source enhancements developed and contributed by Sean Kendall Schneyer (@StrykerSKS), which was in turn based upon the original Open Source device driver provided by SmartThings.

I hereby contribute my edits and additions to the same Open Source domain as the prior works noted above. (Open Source descriptions here). I attest that my edits and modifications are solely my own original work, and not derived, copied or replicated from other non-open source works. Any similarity to other works is therefore a product of development using the Ecobee API and the SmartThings development environment, which independently and together define a narrow band of operational leeway in order to successfully interface with the two environments.

<b>IMPORTANT!</b> Before installing and using my enhanced version, I <i>strongly encourage</i> you to review the most excellent documentation written by @StrykerSKS for his updates. You can find it here: https://github.com/StrykerSKS/SmartThingsPublic/blob/StrykerSKS-Ecobee3/smartapps/smartthings/ecobee-connect.src/README.md

Notable Enhancements and Changes
--------------------------------
This work represents a significant overhaul of the aforemention prior implementation. Most notable of these include:
* <b>Efficiency and Performance</b>
  * A <i>significant</i> reduction in the frequency and amount of data collected from the Ecobee cloud servers. Instead of requesting all the information for all of the thermostats each time the Ecobee API is polled, this implementation will:
    * Do a lightweight <code>"thermostatSummary"</code> poll to determine which thermostat objects have changed (and for which thermostats, if there are more than one) since the last time data was retrieved from the API. The only two objects of interest to this implementation are:
     * <code>thermostat: settings, programs, events, <strike>devices</strike></code>
     * <code>runtime: runtime (temperatures, humidity, etc.), equipmentStatus, remoteSensors, weather</code>
        * The <code>remoteSensors</code> object is only requested if one or more sensors have been selected in the configuration (including showing thermostats as sensors)
        * The <code>weather</code> object does not change as frequently as the runtime object, so it specifically is requested less often than the rest of the objects represented by thermostatSummary(runtime) - (every 15 minutes)
        * This implementation does not use the <code>devices</code>, <code>alerts</code>, or <code>extendedRuntime</code> objects, and therefore never requests them
    * It will then call the Ecobee API to request only the data objects that have indicated changes, and only for the thermostats that have changed (in cases where multiple thermostats are being used);
      * In particular, the <code>thermostat</code> object rarely changes, yet it can represent more than 6000 bytes of data for each thermostat. Not requesting this on every call makes a massive difference...
  
  * A <i>significant</i> reduction in the amount of data sent from the Ecobee (connect) SmartApp to the individual ecobee thermostat device(s):
    * Only data from the changed object(s) is sent. While this is likely to include some individual data elements that have not changed, it does significantly reduce the amount of work the SmartApp and device driver has to do for each update;

    * If debugLevel is set to 3 or lower in the SmartApp, the "Last Poll" date and time is not sent; instead the thermostat devices' UI will show polling status as Succeeded/Incomplete/Failed. Changing the debugLevel in the SmartApp will dynamically cause the child thermostat(s) to begin displaying date of last poll;

* <b>User Interface Enhancements</b>
  * Thermostat devices
    * For systems with heat pumps, multiple heating stages and/or multiple cooling stages, the thermostat device UI will show which device (heat pump/emergency heat) or stage (heat 1/heat 2/cool 1/cool 2) is in operation. Single-stage, non-heat pump devices will show only heating/cooling, and heat pump configurations will properly identify auxHeat as "emergency" heat in the UI;
    * For systems with a humidifier or a dehumidifier the heat and cool icons include an indicator when these are running, and icons are also provided for humidifier and dehumidifier-only operation (where applicable).
      * NOTE: the ecobee3 can only support either a humidifier or a dehumidifier (or a ventilator), while other Smart thermostats can support multiple at the same time. This new thermostat device should handle all combinations EXCEPT HRV/ERV ventilators are not currently supported.
    * The aforementioned change to Last Poll display based upon debugLevel to reduce data transferred from the SmartApp to the thermostat device has the side benefit of virtually eliminating the "chatter" in the devices' "Recent" message log in the Mobile App. This makes it easier to review when various data element changes were reported to the UI.
    * Colors for the background of the main multiAttributeTile have been updated to mimic the colors of the ecobee3 thermostat and web/mobile app UI colors (e.g. flame orange for heating, snowflake blue for cooling, pale green for idle, and a purple/magenta for idle.
    * A status line has been added below the status icons and before the Command Center to report when the currently running hold ends.
    * To the right of the above Hold Status, a small display shows the current setting of fanMinOnTime - the number of minutes the fan should run each hour, whether while in heat/cool, or fan only. This is usually set on the thermostat, and can be specified for Vacation modes; the new Smart Circulation Helper App can now dynamically change this based on the temperature delta between two sensors. See below for more information.
    * Weather icon colors and a few other have also been updated to reflect the ecobee3 thermostat colors (instead of monochromatic blue).
  * Sensor devices
    * A new multiAttributeTile replaces the old presentation, with temperature as the primary display value, and motion displayed in the bottom left corner;
  * All devices
    * Now have the ability to display 0, 1 or 2 decimal places of precision for thermostats and sensors. The default for metric locales is 1 decimal place (e.g. 25.2°) and for imperial locales is 0 (e.g., 71°). These defaults can be overridded in the preferences section Ecobee (Connect) SmartApp, and changes take effect from that point on;
      * NOTE: Currently, changing the display precision also changes the value maintained internally for the SmartThings temperature attributes. This may be changed in the future such that full precision is always maintained internally, and only the display precision is configurable.
      
* <b>Operational Enhancements</b>
  * Resume vs Program change: If a request is made to change the thermostat's Program (<i>aka</i> Climate) to the currently scheduled Program, and the request type is Temporary (<i>aka</i> nextTransition), a Resume is executed instead of setting a new Hold. If a Permanent hold is requested, it is effected directly, and no attempt to resume is made.
  * <code>thermostat.resumeProgram</code> now defaults to <i>always</i> request that ALL outstanding program holds are removed, not just the most recent. 
    * NOTE: This is how the new ecobee3 thermostats operate via their UI and manually. Most Ecobee thermostats support the notion of "stacked" Holds, where each request to change temp/program would "stack" a new Hold on the old, and it would require multiple successive Resume program calls to reset the stack. For simplicity, this version always requests that ALL hold events be cleared on a rresume program request.
    * NOTE: It is still possible programmatically to request that only the most recent hold request is resumed bu calling <code>thermostat.resumeProgram(false)</code>. The new Smart Zone helper app utilizes this to return from its temporary fanOn events. 
    * TODO: Note also that vacation holds <i>cannot</i> be cleared with the current implmentation of resumeProgram. Vacations must be deleted in order to clear them; this author <i>may</i> consider adding the ability to delete a vacation when resumeProgram is called.
  * Polling Frequency: As a result of the aforementioned operational efficiency enhancements, it is now possible to run ecobee thermostat devices with very short polling frequency. Although Ecobee documents that none of the API's data objects are updated more frequently than every 3 minutes, this has been observed to not be true. Runtime updates can happen at practically any time, and it appears that equipmentStatus updates are effected from the thermostat to the cloud and out to the API in nearly real time. I have run for days using a 1-minute polling frequency, with only infrequent (recoverable and recovered) errors. 
    * NOTE: Your mileage may vary - ecobee does in fact recommend <i><u>a minimum of 3 minutes</u></i>, and they may choose to prohibit shorter polling frequencies at any time.
    * It is now possible to select a 2 minute polling frequency, which is perhaps a happy medium between the Ecobee-recommended 3 minute minimum poll time, and the 1 minute frequency used primarily for debugging.
  * Watchdog Devices: A practical alternative to short polling frequency is now to configure one or more "watchdog" devices. In addition to ensuring that the scheduled polls haven't gotten derailed by the SmartThings infrastructure, an event from, say, a temperature change on a SmartThings MultiSensor will also cause a poll of the ecobee API to check if anything has changed. If not, no foul - the "heavy" API call is avoided.

* <b>Helper SmartApps</b>
 * <b>Mode/Routines</b> handler: New "smart" changes to thermostat program:
   * Send a notification to the location's notification log explaining what was done
   * If current program is already the requested program, and we aren't in a hold, then leave it alone
   * If thermostat currently in "hold" mode, and the originally scheduled program is the same as the target, then simply <code>resumeProgram(resumeAll=true)</code>
 * New <b>Smart Circulation</b> Handler: mimics the latent (not yet enabled) ecobee3 function of the same name. Monitors the temperature across 2 or more sensors (not necessarily ecobee sensors - ANY ST thermometer will do), and if the temperature delta between the highest and lowest reading is greater than a configurable range, increases the minimum fan on time (automated circulation). Reduces the fanMinOnTime again when min/max temperatures are within 1F/0.5C of each other. Minimum and maximum fan circulation time (minutes per hour) and frequency of updating are also configurable.
   * Update on 2017-01-29 @ ~8:50pm EST adds ability to adjust fan circulation time while thermostat is in a Vacation Hold (failed silently in prior releases). Enabled via new setting in preferences (default is NOT to modify fanMinOnTime while in Vacation hold).
 * New <b>Smart Zone</b> Handler: attempts to have ALL zones on a single HVAC synchronize their circulation schedule, so that the HVAC fan isn't run independently for each zone (thereby reducing electricity demand). 
   * Any time the 'master' zone is running 'fan only', Smart Zone will turn on the fans in the slave zone(s). 
     * NOTE: this will create a temporary hold state ('Hold: Fan') on the slave thermostat(s), which will be cleared after the fan override completes.
    * When the master thermostat returns to 'idle', 'heat' or 'cool', the slave program is resumed - <i>i.e.</i>, the Hold: Fan is popped off the hold stack
 
* <b>Other Miscellaneous Enhancements</b>
  * SmartApp Name: it is now possible to rename each instance of the Ecobee (Connect) SmartApp. This is useful for those with multiple locations/hubs.
  * Contact list support: For those who have enabled SmartThings contact lists, you can now select which users will recieve Push notifications of major issues (e.g., warnings about API connection loss). If the contact list is not enabled, a Push message is sent to all users (as before)
  * The Push message will include the location name in the message (again, for users with multiple locations)
  * Child apps that request a pollChildren(this) now specify this should set the forcePoll flag to get ALL data from the Ecobee cloud, for ALL thermostats. This makes pushing the refresh button VERY heavy operation, but it can sometimes be necessary to unjam the data flow.
  * Added support for humidifier & dehumidifier in equipment status, both with heat/cool, and as standalone aoperations - with new/updated icons
  * Fixed vacation hold display, added airplane icon
  * Helper Applications: The Mode/Program helper app will now add a message to the location's Notifications log when it changes the thermostats' Program (Climate). This should appear as an extension to the <code>"I have changed mode to [SmartThings mode]..."</code> notification, and appears as <code>"and I have also set the [thermostat name] to [thermostat program name]</code>"
  * Thermostat attributes: Virtually ALL of the significant thermostat attributes provided by the Ecobee API are now reflected in the thermostat device as Attributes. This allows SmartThings developers to query more detail about a specific thermostat without having to interface to the API directly. The accessible Attributes (and the current values) appear in the device report for each thermostat; these can be accessed programatically as <code><i>deviceId</i>.currentValue("<i>AttributeName</i>")</code>
  * Internal Data Store: Of necessity to impliment its efficient use of the Ecobee API, the Ecobee (Connect) SmartApp maintains a rather large amount of static data (in atomicState). This adds some computational and memory overhead, and therefor every effort has been made to reduce repeated calls for atomicState data, to store as little data as efficiently possible, and to update that data as infrequently as possible.
    * Each of the Ecobee API data objects is now stored in a separate atomicState Map. The Map <code>atomicState.thermostatData[]</code> no longer stores ALL the data for ALL the thermostats and sensors. Instead, it only includes the requested subset of data returned by the last call to the Ecobee API. (This necessitated an extensive overhaul of the original code to change how the stored data is accessed, since each object can now be independently updated).
    * NOTE: There is still room to further reduce the amount of data stored in atomicState, I just haven't felt the urge to delve into the operational complexity this would add. Unless SmartThings says we have to change this, it will likely remain as-is.
 * Changed weather icons to use the 3-color scheme of the thermostat (cyan, white, gold), plus grey for fog/smoke/haze
 * Deleted a lot of code that was no longer being used
  
* <b>Bug Fixes</b>
  * The prior version would sometimes truncate decimal values to an integer, changing (for example) 72.8° into 72°, while the thermostat   itself would display this as 73°. All decimal values are now mathematically rounded, such that 72.4° displays as 72°, and 72.5° displays as 73° (if decimal precision is set to 0).
  * The prior version defined a "sleep()" command, and allowed you to request Sleep mode via button press in the UI - these never worked (in fact, silently failed) because it is not possible to overload the built-in sleep() function of the SmartThings/Groovy/Amazon S3 environment. Thus, the sleep() call has been replaced with two new calls: asleep() and night(), and the UI now properly supports changing to sleep() mode.
 
Notes, Warning and Caveats
--------------------------
* This work is provided as-is, with no assurance of effectiveness or warranty of any kind, including no protections against defects or damage. <b><i>Use at your own risk.</i></b>
* Using polling frequencies shorter than 3 minutes is at the user's risk - this may or may not work, and ecobee may choose to inhibit this at any time, without notice.  <b><i>Use at your own risk.</i></b>
* The Celsius support has NOT been tested, and may well have been broken. If so, let me know and I'll try to fix it.  <b><i>Use at your own risk.</i></b>
* That said, the Fahrenheit support has not been tested exhausitvely either (I can't test cooling mode right now, in the dead of New England winter, for example).  <b><i>Use at your own risk.</i></b>
* Oh, and  <b><i>Use at your own risk.</i></b>
 
  
