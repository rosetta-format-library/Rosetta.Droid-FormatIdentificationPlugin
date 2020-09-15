
How to upgrade Droid:

1) Under the lib folder, replace droid-container-6.5.jar, droid-core-6.5.jar and droid-core-interfaces-6.5.jar with newer version.
2) Edit FFDroidIdentificationPlugin.java- Change the AGENT_VERSION variable to the new version.
3) Edit FFDroidIdentificationPlugin.java- Change the SIG_VERSION variable to the new version.
4) Increase the pl:version in the src/PLUGIN-INF/metadata_FFDroidIdentificator.xml
5) Under the conf folder, replace DROID_SignatureFile.xml with the related one from: https://www.nationalarchives.gov.uk/aboutapps/pronom/droid-signature-files.htm
6) Under the conf folder, replace container-signature.xml with the related one from: https://www.nationalarchives.gov.uk/aboutapps/pronom/droid-signature-files.htm
7) Build the plugin with command line: $ant
