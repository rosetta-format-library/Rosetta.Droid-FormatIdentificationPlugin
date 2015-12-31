package com.exlibris.dps.repository.plugin.formatidentification;

import java.util.ArrayList;
import java.util.List;

import com.exlibris.core.sdk.consts.Enum.FormatIdentificationMethod;
import com.exlibris.dps.sdk.formatidentification.FormatIdentificationResult;

/**
 *
 * @author Yuliar
 *
 */
public class DroidFormatIdentificationResult implements FormatIdentificationResult {

	private List<String> formats;
	private FormatIdentificationMethod method;

	// ValidationStackErrorConstant
	private long vsError;
	private boolean isPositiveMatch = false;

	@Override
	public List<String> getFormats() {
		return formats;
	}

	@Override
	public FormatIdentificationMethod getIdentificationMethod() {
		return method;
	}

	public void setFormat(List<String> formats) {
		this.formats = formats;
	}

	public void setMethod(FormatIdentificationMethod method) {
		this.method = method;
	}

	public void addFormat(String puid) {
		if(formats == null){
			formats = new ArrayList<String>();
		}
		formats.add(puid);
	}

	public void setVsError(long vsError) {
		this.vsError = vsError;
	}

	public long getVsError() {
		return vsError;
	}

	public void setPositiveMatch(boolean isPositiveMatch) {
		this.isPositiveMatch = isPositiveMatch;
	}

	public boolean isPositiveMatch() {
		return isPositiveMatch;
	}
}