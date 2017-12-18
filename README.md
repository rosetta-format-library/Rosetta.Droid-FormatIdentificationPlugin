
How to upgrade Droid:

1) Under the lib folder, replace droid-container-6.3.jar, droid-core-6.3.jar and droid-core-interfaces-6.3.jar with newer version.
2) Edit FFDroidIdentificationPlugin.java- Change the AGENT_VERSION variable to the new version.
3) Edit FFDroidIdentificationPlugin.java- Change the SIG_VERSION variable to the new version.
4) Increase the pl:version in the src/PLUGIN-INF/metadata_FFDroidIdentificator.xml
