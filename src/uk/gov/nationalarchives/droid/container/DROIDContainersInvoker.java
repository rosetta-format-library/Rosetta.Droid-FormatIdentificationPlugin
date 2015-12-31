package uk.gov.nationalarchives.droid.container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.gov.nationalarchives.droid.container.AbstractContainerIdentifier;
import uk.gov.nationalarchives.droid.container.ContainerSignature;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.container.ContainerSignatureMatch;
import uk.gov.nationalarchives.droid.container.ContainerSignatureMatchCollection;
import uk.gov.nationalarchives.droid.container.FileFormatMapping;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultImpl;

public class DROIDContainersInvoker {

	private ContainerSignatureDefinitions containerSigDef;

	public ContainerSignatureDefinitions getContainerSigDef() {
		return containerSigDef;
	}

	public void setContainerSigDef(ContainerSignatureDefinitions containerSigDef) {
		this.containerSigDef = containerSigDef;
	}

	/**
	 * Invokes the relevant container identifier on the file for format identification
	 * @param containerIdentifier
	 * @param request
	 * @param containerType
	 * @param maxBytesToScan
	 * @return DROID identification results
	 * @throws IOException
	 */
	public IdentificationResultCollection invoke(AbstractContainerIdentifier containerIdentifier, IdentificationRequest request, String containerType, long maxBytesToScan) throws IOException{

		Map<Integer, List<FileFormatMapping>> formats = new HashMap<Integer, List<FileFormatMapping>>();
		for (FileFormatMapping fmt : containerSigDef.getFormats()) {
	        List<FileFormatMapping> mappings = formats.get(fmt.getSignatureId());
	        if (mappings == null) {
	            mappings = new ArrayList<FileFormatMapping>();
	            formats.put(fmt.getSignatureId(), mappings);
	        }
	        mappings.add(fmt);
	    }
		containerIdentifier.setFormats(formats);

		Set<String> uniqueFileSet = new HashSet<String>();
	    for (ContainerSignature sig : containerSigDef.getContainerSignatures()) {
	        if (sig.getContainerType().equals(containerType)) {
	        	containerIdentifier.addContainerSignature(sig);
	            uniqueFileSet.addAll(sig.getFiles().keySet());
	        }
	    }
	    List<String> uniqueFileEntries = new ArrayList<String>(uniqueFileSet);
        ContainerSignatureMatchCollection matches =
            new ContainerSignatureMatchCollection(containerIdentifier.getContainerSignatures(), uniqueFileEntries, maxBytesToScan);


        containerIdentifier.process(request, matches);

        IdentificationResultCollection results = new IdentificationResultCollection(request);
        for (ContainerSignatureMatch match : matches.getContainerSignatureMatches()) {
            if (match.isMatch()) {
                List<FileFormatMapping> mappings = formats.get(match.getSignature().getId());
                for (FileFormatMapping mapping : mappings) {
                    IdentificationResultImpl result = new IdentificationResultImpl();
                    result.setMethod(IdentificationMethod.CONTAINER);
                    result.setRequestMetaData(request.getRequestMetaData());
                    result.setPuid(mapping.getPuid());
                    results.addResult(result);
                }
            }
        }

        return results;
	}
}
