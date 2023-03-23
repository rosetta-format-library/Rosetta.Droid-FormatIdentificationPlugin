package com.exlibris.dps.repository.plugin.formatidentification;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.core.signature.droid6.FFSignatureFile;

import com.exlibris.core.infra.common.exceptions.logging.ExLogger;
import com.exlibris.core.infra.svc.api.services.CacheServices;
import com.exlibris.core.sdk.strings.StringUtils;
import com.exlibris.dps.sdk.formatidentification.FormatIdentificationPlugin;
import com.exlibris.dps.sdk.formatidentification.FormatIdentificationResult;

public class FFDroidIdentificationPlugin implements FormatIdentificationPlugin {
	private static final String PLUGIN_VERSION_INIT_PARAM = "PLUGIN_VERSION_INIT_PARAM";
	private static String AGENT_NAME = "REG_SA_DROID";
	private static String AGENT_VERSION = "6.5.2";
	private static String REGISTRY_NAME = "PRONOM";
	private long maxBytesToScan = -1;
	private boolean inTag = false;
	private static FFSignatureFile sigFile;
	private static ContainerSignatureDefinitions containerSigDef;
	private static String pluginVersion = null;

	private static final ExLogger log = ExLogger.getExLogger(FFDroidIdentificationPlugin.class);

	/*
	 * this parameter is used for signature file caching. When new signature file
	 * attached, update SIG_VERSION Or 2 different plugins with the same jar path
	 * and jar name
	 */
	private static String SIG_VERSION = "_109";

	static {
		Map map=null;
		try {
			try {
				map = (Map) CacheServices.getInstance().getCache(CacheServices.DROID_SIGNATURE + SIG_VERSION);
			}catch (Exception e) {
				log.error("Failed parsing signature files", e);
			}
			if (map == null) {
				loadSigVersionsToCache();
			}
			ArrayList<Object> sigList = (ArrayList<Object>) CacheServices.getInstance().getCacheValue(
					CacheServices.DROID_SIGNATURE + SIG_VERSION, CacheServices.DROID_SIGNATURE + SIG_VERSION);
			sigFile = (FFSignatureFile) sigList.get(0);
			containerSigDef = (ContainerSignatureDefinitions) sigList.get(1);
		} catch (Exception e) {
			log.error("Failed parsing signature files", e);
		}
	}

	@Override
	public String getAgentName() {
		return AGENT_NAME;
	}

	@Override
	public String getAgentSignatureVersion() {
		/*
		 * ('Binary SF v.X / Container SF v.Y'for the DROID 6 format identification
		 * plugin, where X is extracted from the binary signature file header
		 * <FFSignatureFile DateCreated="2011-09-07T00:15:08" Version="52" and Y is
		 * extracted from the container signature file header
		 * (<ContainerSignatureMapping schemaVersion="1.0" signatureVersion="1">)
		 */
		String sigVersion = "";
		String containerVersion = "";
		try {
			sigVersion = sigFile.getVersion();
			InputStream containerIs = this.getClass().getClassLoader()
					.getResourceAsStream("conf/container-signature.xml");
			BufferedReader containerBis = new BufferedReader(new InputStreamReader(containerIs));
			if (containerBis.ready()) {
				while (containerVersion == null || containerVersion.trim().isEmpty()) {
					containerVersion = parseVersion(containerBis.readLine(), "containersignaturemapping",
							"signatureversion");
				}
			}
		} catch (Exception e) {
			log.error("Failed to get agent signature version", e);
		}
		return "Binary SF v." + sigVersion + "/ Container SF v." + containerVersion;
	}

	@Override
	public String getAgentVersion() {
		return AGENT_VERSION;
	}

	@Override
	public String getFormatRegistryName() {
		return REGISTRY_NAME;
	}

	@Override
	public FormatIdentificationResult identifyFormat(String filePath) {
		// run binary identification
		DroidResourceUtil droidResource = new DroidResourceUtil(pluginVersion);
		droidResource.setContainerSigDef(containerSigDef);
		droidResource.setSigFile(sigFile);
		droidResource.setMaxBytesToScan(maxBytesToScan);
		FormatIdentificationResult results = null;
		try {
			results = droidResource.runFormatIdentification(filePath);
		} catch (Exception e) {
			log.error("Failed identify format", e);
		}
		return results;
	}

	public void setMaxBytesToScan(long maxBytesToScan) {
		this.maxBytesToScan = maxBytesToScan;
	}

	public long getMaxBytesToScan() {
		return maxBytesToScan;
	}

	/**
	 * This method is called from the PluginLocator-PluginInvokationHandler.
	 * 
	 * @param initParams
	 */
	public void initParams(Map<String, String> initParams) {
		if (!StringUtils.isEmptyString(initParams.get("maxBytesToScan"))) {
			maxBytesToScan = Long.parseLong(initParams.get("maxBytesToScan").trim());
		}
		pluginVersion = initParams.get(PLUGIN_VERSION_INIT_PARAM);
	}

	private String parseVersion(String readLine, String firstTag, String secondTag) {
		if (readLine == null) {
			return null;
		}
		if (readLine.trim().isEmpty()) {
			return "";
		}
		readLine = readLine.toLowerCase();
		if (inTag || readLine.indexOf(firstTag) > -1) {
			inTag = true;
			int versionInd = readLine.indexOf(secondTag);
			if (versionInd > -1) {
				readLine = readLine.substring(versionInd + secondTag.length() + 1);
				if (readLine == null || readLine.trim().isEmpty()) {
					return "";
				}
				readLine = readLine.trim();
				if (readLine.startsWith("\"")) {
					readLine = readLine.substring(1);
					if (readLine == null || readLine.trim().isEmpty()) {
						return "";
					}
					readLine = readLine.trim();
				}
				versionInd = readLine.indexOf("\"");
				if (versionInd > -1) {
					readLine = readLine.substring(0, versionInd);
					return readLine;
				}
			}
		}
		return "";
	}

	private static void loadSigVersionsToCache() {
		try {
			DroidResourceUtil droidResource = new DroidResourceUtil(pluginVersion);
			FFSignatureFile sigFile = droidResource.readSigFile("conf/DROID_SignatureFile.xml");
			ContainerSignatureDefinitions containerSigDef = droidResource
					.readContSigFile("conf/container-signature.xml");
			HashMap<Object, Object> map = new HashMap<Object, Object>();
			List<Object> sigList = new ArrayList<Object>();
			sigList.add(sigFile);
			sigList.add(containerSigDef);
			map.put(CacheServices.DROID_SIGNATURE + SIG_VERSION, sigList);
			CacheServices.getInstance().setCacheValue(CacheServices.DROID_SIGNATURE + SIG_VERSION, map, false);
		} catch (Exception e) {
			log.error("Failed loading signature files to cache", e);
		}
	}
}
