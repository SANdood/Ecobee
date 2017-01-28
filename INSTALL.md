Installation Instructions
=========================
If you have been using an earlier version of the ecboee Device Handler, either the original from SmartThings or the 
updated version from SKStryker, <b>your best bet is to remove those devices and SmartApps from your environment</b>
before installing this version.

However, this version has been designed in hopes to be able to simply save/publish over your existing version without 
breaking anything, so if you're feeling gutsy, go right ahead. Let me know how it works (you very likely will have to 
re-authorize with Ecobee once after you get everything installed and published).

1. Copy the following into your IDE section "My SmartApps", overwriting existing versions or 'New SmartApp / From Code'
  * <code>ecobee-connect.groovy</code>
  * <code>ecobee-open-contacts.groovy</code>
  * <code>ecobee-routines.groovy</code>
  * <code>ecobee-smart-circulation.groovy</code>
  * <code>ecobee-smart-zones.groovy</code>
  
2. Save and 'Publish / For Me' each of the above. 
<b>DO NOT try to run any of these until you have completed step 4 below!</b>

3. Copy the following into your IDE section "My Device Handlers", overwriting existing versions or 'Create New Device Handler / From Code'
  * <code>ecobee-sensor.groovy</code>
  * <code>ecobee-thermostat.groovy</code>

4. Save and 'Publish/For Me' both of the above

5. Now, run the Ecobee (Connect) SmartApp. 
  * If you installed on top of your existing envirnment, you can find it in the Automation Tab, under SmartApps
    * Verify your configuration is intact
    * Update your Preferences section - there are 2 new preferences (Notifications at the top, and Display precision at 
    the bottom). 
    There are also new chioces for polling frequency (use 1 minute frequency at your own risk)
  * If this is a new/fresh install, you'll get to configure everything. I suggest holding off on the Helper SmartApps until 
  you know things are working.
  
6. When you finish the configuration, hit "Done".

7. Now, go select your thermostat(s) and check out the updated UI.

8. For a thorough review of the updates and new features, be sure to read the README file in this repository.
