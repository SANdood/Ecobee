Installation Instructions
=========================
<b>IMPORTANT!</b> Before installing and using my enhanced version, I <i>strongly encourage</i> you to review the most excellent documentation written by @StrykerSKS for his updates. You can find it here: https://github.com/StrykerSKS/SmartThingsPublic/blob/StrykerSKS-Ecobee3/smartapps/smartthings/ecobee-connect.src/README.md

<b>NOTE: </b> You should follow Sean's instructions for <b>Install Manually from Code</b>, installing <i>and publishing</i> all five SmartApps and the two Device Handlers described below.

If you have been using an earlier version of the ecboee Device Handler, either the original from SmartThings or the 
updated version from @StrykerSKS, <b>your best bet is to remove those devices and SmartApps from your environment</b>
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
<b>DO NOT try to run any of these until you have completed step 5 below!</b>

3. For Ecobee (Connect) SmartApp, be sure to enable OAuth. In the IDE, while you have Ecobee (Connect) open, Click on "App Settings" (top right), then select the OAuth tab and enable OAuth.

   <b>NOTE:</b> <i>This is a commonly missed step, but failing to enable OAuth will generate cryptic errors later when you try to use the SmartApp. So please don't skip this step.)</i>

4. Copy the following into your IDE section "My Device Handlers", overwriting existing versions or 'Create New Device Handler / From Code'
  * <code>ecobee-sensor.groovy</code>
  * <code>ecobee-thermostat.groovy</code>

5. Save and 'Publish/For Me' both of the above

6. Now, run the Ecobee (Connect) SmartApp. 
  * If you installed on top of your existing envirnment, you can find it in the Automation Tab, under SmartApps
    * Verify your configuration is intact
    * Update your Preferences section - there are 2 new preferences (Notifications at the top, and Display precision at 
    the bottom). 
    There are also new chioces for polling frequency (use 1 minute frequency at your own risk)
  * If this is a new/fresh install, you'll get to configure everything. I suggest holding off on the Helper SmartApps until 
  you know things are working.
  
7. When you finish the configuration, hit "Done".

8. Now, go select your thermostat(s) and check out the updated UI.

9. For a thorough review of the updates and new features, be sure to read the README file in this repository.
