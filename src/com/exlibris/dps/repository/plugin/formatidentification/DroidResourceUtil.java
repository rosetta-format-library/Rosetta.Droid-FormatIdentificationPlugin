package com.exlibris.dps.repository.plugin.formatidentification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import uk.gov.nationalarchives.droid.container.AbstractContainerIdentifier;
import uk.gov.nationalarchives.droid.container.ContainerFileIdentificationRequestFactory;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.container.ContainerSignatureSaxParser;
import uk.gov.nationalarchives.droid.container.DROIDContainersInvoker;
import uk.gov.nationalarchives.droid.container.TriggerPuid;
import uk.gov.nationalarchives.droid.container.ole2.Ole2Identifier;
import uk.gov.nationalarchives.droid.container.ole2.Ole2IdentifierEngine;
import uk.gov.nationalarchives.droid.container.zip.ZipIdentifier;
import uk.gov.nationalarchives.droid.container.zip.ZipIdentifierEngine;
import uk.gov.nationalarchives.droid.core.IdentificationRequestByteReaderAdapter;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResult;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultImpl;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.archive.ArchiveFormatResolver;
import uk.gov.nationalarchives.droid.core.interfaces.archive.ArchiveFormatResolverImpl;
import uk.gov.nationalarchives.droid.core.interfaces.archive.ContainerIdentifierFactory;
import uk.gov.nationalarchives.droid.core.interfaces.archive.ContainerIdentifierFactoryImpl;
import uk.gov.nationalarchives.droid.core.interfaces.resource.FileSystemIdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;
import uk.gov.nationalarchives.droid.core.signature.ByteReader;
import uk.gov.nationalarchives.droid.core.signature.FileFormat;
import uk.gov.nationalarchives.droid.core.signature.FileFormatCollection;
import uk.gov.nationalarchives.droid.core.signature.FileFormatHit;
import uk.gov.nationalarchives.droid.core.signature.droid6.FFSignatureFile;
import uk.gov.nationalarchives.droid.core.signature.xml.SAXModelBuilder;

import com.exlibris.core.infra.common.exceptions.logging.ExLogger;
import com.exlibris.core.sdk.consts.Enum.FormatIdentificationMethod;
import com.exlibris.core.sdk.utils.FSUtil;
import com.exlibris.dps.sdk.formatidentification.FormatIdentificationResult;

public class DroidResourceUtil {

	public static final String SIGNATURE_FILE_NS = "http://www.nationalarchives.gov.uk/pronom/SignatureFile";

	private static final String CONTAINER_ERROR = "Could not process the potential container format (%s): %s\t%s\t%s";
	private static String droidDir = FSUtil.getSystemDir() + "conf/droid/";
	private static String defaultDroidSigFigFile = FSUtil.getSystemDir() + "conf/container-signature.xml";

	private FFSignatureFile sigFile;
	private ContainerSignatureDefinitions containerSigDef;
	private long maxBytesToScan = 65536;
	private ArchiveFormatResolver containerFormatResolver = null;
	private ContainerIdentifierFactory containerIdentifierFactory = null;
	private String pluginVersion = null;
	private static final ExLogger log = ExLogger.getExLogger(DroidResourceUtil.class);

	public DroidResourceUtil(String pluginVersion) {
		this.pluginVersion = pluginVersion;
	}

	/**
	 * Reads and parses binary signature file.
	 * @param droidSignatureFileName
	 * @return the parsed binary signature file as a DROID object
	 * @throws SignatureParseException
	 * @throws IOException
	 */
	public FFSignatureFile readSigFile(String droidSignatureFileName) throws SignatureParseException, IOException {
		FFSignatureFile signatureFile = null;
		SAXModelBuilder mb = new SAXModelBuilder();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser saxParser;
		XMLReader parser;
		try {
			saxParser = factory.newSAXParser();
			parser = saxParser.getXMLReader();
			mb.setupNamespace(SIGNATURE_FILE_NS, true);
			parser.setContentHandler(mb);
		} catch (ParserConfigurationException e) {
			throw new SignatureParseException(e.getMessage(), e);
		} catch (SAXException e) {
			throw new SignatureParseException(e.getMessage(), e);
		}

		// read in the XML file
		InputStream sigIs = null;
		BufferedReader in = null;
		try {
			sigIs = this.getClass().getClassLoader().getResourceAsStream(droidSignatureFileName);
			in = new BufferedReader(new InputStreamReader(sigIs));
			parser.parse(new InputSource(in));
		} catch (IOException e) {
			throw new SignatureParseException(e.getMessage(), e);
		} catch (SAXException e) {
			throw new SignatureParseException(e.getMessage(), e);
		}
		finally{
			if(in != null){
				in.close();
			}
			if(sigIs != null){
				sigIs.close();
			}
		}
		signatureFile = (FFSignatureFile) mb.getModel();

		signatureFile.prepareForUse();

		return signatureFile;
	}
	/**
	 * Reads and parses container signature file.
	 * @param filePath
	 * @return the parsed container signature file as a DROID object
	 * @throws JAXBException
	 * @throws SignatureParseException
	 */
	public ContainerSignatureDefinitions readContSigFile(String filePath) throws JAXBException, SignatureParseException {
		ContainerSignatureSaxParser signatureFileParser = new ContainerSignatureSaxParser();
		return signatureFileParser.parse(this.getClass().getClassLoader().getResourceAsStream(filePath));
	}

	public void removeLowerPriorityHits(IdentificationResultCollection results) {
		// Build a set of format ids the results have priority over:
		writeToLog("before removeLowerPriorityHits ",results);
		FileFormatCollection allFormats = sigFile.getFileFormatCollection();
		Set<Integer> lowerPriorityIDs = new HashSet<Integer>();
		for (IdentificationResult result : results.getResults()) {
			final String resultPUID = result.getPuid();
			final FileFormat format = allFormats.getFormatForPUID(resultPUID);
			lowerPriorityIDs.addAll(format.getFormatIdsHasPriorityOver());
		}

		// If a result has an id in this set, add it to the remove list;
		List<IdentificationResult> lowerPriorityResults = new ArrayList<IdentificationResult>();
		for (IdentificationResult result : results.getResults()) {
			final String resultPUID = result.getPuid();
			final FileFormat format = allFormats.getFormatForPUID(resultPUID);
			if (lowerPriorityIDs.contains(format.getID())) {
				lowerPriorityResults.add(result);
			}
		}

		// Now remove any lower priority results from the collection:
		for (IdentificationResult result : lowerPriorityResults) {
			results.removeResult(result);
		}
		writeToLog("after removeLowerPriorityHits ", results);
	}

	private void writeToLog(String msg, IdentificationResultCollection results) {
		StringBuffer logInfo = new StringBuffer();
		logInfo.append(msg);
		if(results != null && results.getResults() != null && results.getResults().size()>0){
			Iterator<IdentificationResult> iter = results.getResults().iterator();
			while(iter.hasNext()){
				IdentificationResult result = iter.next();
				logInfo.append(result.getPuid()).append(" ").append(result.getMethod()).append("; \t");;
			}
		}
		log.info(logInfo.toString());
	}
	/**
	 * Run binary signature identification on the file
	 * @param request
	 */
	public IdentificationResultCollection runBinarySignatureIdentification(String filePath, IdentificationRequest request){
		IdentificationResultCollection results = null;
		results = new IdentificationResultCollection(request);
		results.setRequestMetaData(request.getRequestMetaData());
		ByteReader byteReader = new IdentificationRequestByteReaderAdapter(request);
		sigFile.setMaxBytesToScan(maxBytesToScan);
		sigFile.runFileIdentification(byteReader);
		final int numHits = byteReader.getNumHits();
		for (int i = 0; i < numHits; i++) {
			FileFormatHit hit = byteReader.getHit(i);
			IdentificationResultImpl result = new IdentificationResultImpl();
			result.setMimeType(hit.getMimeType());
			result.setName(hit.getFileFormatName());
			result.setVersion(hit.getFileFormatVersion());
			result.setPuid(hit.getFileFormatPUID());
			result.setMethod(IdentificationMethod.BINARY_SIGNATURE);
			results.addResult(result);
		}
		results.setFileLength(request.size());
		results.setRequestMetaData(request.getRequestMetaData());
		removeLowerPriorityHits(results);
		return results;
	}

	/**
	 * Run container identification on the file
	 * @param request
	 * @param identifier
	 * @param metaData
	 * @throws IOException
	 */
	public IdentificationResultCollection runContainerIdentification(String filePath, IdentificationResultCollection results,
			IdentificationRequest request, RequestMetaData metaData, RequestIdentifier identifier) throws Exception {
		if(results == null || results.getResults() == null || results.getResults().isEmpty()){
			return null;
		}
		try {
			results = handleContainer(request, results, metaData, identifier);
		} catch (Exception e) {
			log.error("Container signature identification failure");
			throw e;
		}
		return results;
	}

	private IdentificationResultCollection handleContainer(IdentificationRequest request, IdentificationResultCollection results,
			RequestMetaData metaData, RequestIdentifier identifier)
	throws Exception {
		// process a container format (ole2, zip, etc)
		String containerFormat = getContainerFormat(results);
		try {
			if (containerFormatResolver != null && containerFormat != null) {
				AbstractContainerIdentifier containerIdentifier = (AbstractContainerIdentifier)containerIdentifierFactory.getIdentifier(containerFormat);
				containerIdentifier.setSignatureFilePath(getsigFile(pluginVersion));
				containerIdentifier.setSignatureFileParser(new ContainerSignatureSaxParser());
				containerIdentifier.setContainerFormatResolver(containerFormatResolver);
				containerIdentifier.setContainerIdentifierFactory(containerIdentifierFactory);
				containerIdentifier.setContainerType(containerFormat);
				containerIdentifier.setMaxBytesToScan(maxBytesToScan);
				containerIdentifier.init();
				DROIDContainersInvoker invoker = new DROIDContainersInvoker();
				invoker.setContainerSigDef(containerSigDef);
				IdentificationResultCollection containerResults = invoker.invoke(containerIdentifier, request, containerFormat, maxBytesToScan);
				removeLowerPriorityHits(containerResults);
				return containerResults.getResults().isEmpty() ? null : containerResults;
			}
		} catch (Exception e) {
			String causeMessage = "";
			if (e.getCause() != null) {
				causeMessage = e.getCause().getMessage();
			}
			String message = String.format(CONTAINER_ERROR,
					containerFormat, request.getIdentifier().getUri().toString(), e.getMessage(), causeMessage);
			log.warn(message);
			throw e;
		}
		return results;
	}

	private String getContainerFormat(IdentificationResultCollection results) {
		for (IdentificationResult result : results.getResults()) {
			final String format = containerFormatResolver.forPuid(result.getPuid());
			if (format != null) {
				return format;
			}
		}

		return null;
	}


	public String getsigFile(String uniqueId) {
    	String confSigPath = droidDir + uniqueId + File.separator + "container-signature.xml";
    	File sigFile = new File(confSigPath);
    	if(!sigFile.exists()) {
    		InputStream is = null;
    		FileOutputStream os = null;
    		try {

    			//copy container-signature.xml file to temp dir
    			sigFile.getParentFile().mkdirs();
    			sigFile.createNewFile();
    			is = this.getClass().getClassLoader().getResourceAsStream("conf/container-signature.xml");
    			os = new FileOutputStream(sigFile);
    			IOUtils.copy(is, os);
    			IOUtils.closeQuietly(is);
    			IOUtils.closeQuietly(os);

    		} catch(Exception e) {
    			log.warn("failed to copy plugin's container-signature.xml for " + uniqueId + ". using default container-signature.xml from dps_conf", e);
    			return defaultDroidSigFigFile;
    		} finally {
    			IOUtils.closeQuietly(is);
    			IOUtils.closeQuietly(os);
    		}
    	}

    	return confSigPath;
    }

	public FFSignatureFile getSigFile() {
		return sigFile;
	}

	public void setSigFile(FFSignatureFile sigFile) {
		this.sigFile = sigFile;
	}

	public ContainerSignatureDefinitions getContainerSigDef() {
		return containerSigDef;
	}

	public void setContainerSigDef(
			ContainerSignatureDefinitions containerSigDef) {
		this.containerSigDef = containerSigDef;
		containerFormatResolver = new ArchiveFormatResolverImpl();
		containerIdentifierFactory = new ContainerIdentifierFactoryImpl();
		// extract trigger puid and the type of identifier that should run on that file
		for (TriggerPuid triggerPuid : containerSigDef.getTiggerPuids()) {
			final String puid = triggerPuid.getPuid();
			containerFormatResolver.registerPuid(puid, triggerPuid.getContainerType());
		}
		/*
		 * Currently only 2 container types are implemented by droid 6.01 - OLE2 and ZIP.
		 * New container implementation should be mapped here
		 */

		Ole2Identifier ole2Identifier = new Ole2Identifier();
		Ole2IdentifierEngine ole2IdentifierEngine = new Ole2IdentifierEngine();
		ole2IdentifierEngine.setRequestFactory(new ContainerFileIdentificationRequestFactory());
		ole2Identifier.setIdentifierEngine(ole2IdentifierEngine);
		containerIdentifierFactory.addContainerIdentifier("OLE2", ole2Identifier);

		ZipIdentifier zipIdentifier = new ZipIdentifier();
		ZipIdentifierEngine zipIdentifierEngine = new ZipIdentifierEngine();
		zipIdentifierEngine.setRequestFactory(new ContainerFileIdentificationRequestFactory());
		zipIdentifier.setIdentifierEngine(zipIdentifierEngine);
		containerIdentifierFactory.addContainerIdentifier("ZIP", zipIdentifier);
	}

	public void setMaxBytesToScan(long maxBytesToScan) {
		this.maxBytesToScan = maxBytesToScan;
	}

	public long getMaxBytesToScan() {
		return maxBytesToScan;
	}
	public FormatIdentificationResult runFormatIdentification(String filePath) throws IOException{
		InputStream in = null;
		IdentificationRequest request = null;
		IdentificationResultCollection results = null;
		try {
			File file = new File(filePath);
			URI resourceUri = file.toURI();

			in = new FileInputStream(file);
			RequestMetaData metaData = new RequestMetaData(file.length(), file.lastModified(), file.getName());
			RequestIdentifier identifier = new RequestIdentifier(resourceUri);
			identifier.setParentId(1L);
			request = new FileSystemIdentificationRequest(metaData, identifier);
			request.open(in);

			results  = runBinarySignatureIdentification(filePath, request);
			if(results != null && results.getResults() != null && results.getResults().size() == 1){
				// run container identification
				try{
					IdentificationResultCollection collResults = runContainerIdentification(filePath, results, request, metaData, identifier);
					if(collResults != null){
						results = collResults;
					}
				}
				catch (Exception e) {
					log.error("DROID container signature identification failed "+filePath, e);
				}
			}
		}
		catch (IOException e) {
			log.error("DROID signature identification failure "+filePath, e);
			throw e;
		}
		finally{
			if(in != null){
				in.close();
			}
			if(request != null){
				request.close();
			}
		}
		return processDroidResults(results);
	}

	private FormatIdentificationResult processDroidResults(
			IdentificationResultCollection results) {
		if(results == null || results.getResults() == null || results.getResults().isEmpty()){
			return null;
		}
		DroidFormatIdentificationResult formatResults = new DroidFormatIdentificationResult();
		Iterator<IdentificationResult> iter = results.getResults().iterator();
		formatResults.setMethod(FormatIdentificationMethod.SIGNATURE);
		while(iter.hasNext()){
			IdentificationResult result = iter.next();
			formatResults.addFormat(result.getPuid());
			if(FormatIdentificationMethod.CONTAINER.toString().equalsIgnoreCase(result.getMethod().getMethod())){
				formatResults.setMethod(FormatIdentificationMethod.CONTAINER);
			}
		}
		return formatResults;
	}

}