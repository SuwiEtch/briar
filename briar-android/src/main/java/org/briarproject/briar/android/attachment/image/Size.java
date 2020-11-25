package org.briarproject.briar.android.attachment.image;

import androidx.annotation.VisibleForTesting;

public class Size {

	final int width;
	final int height;
	final String mimeType;
	final boolean error;

	public Size(int width, int height, String mimeType) {
		this.width = width;
		this.height = height;
		this.mimeType = mimeType;
		this.error = false;
	}

	@VisibleForTesting
	public Size() {
		this.width = 0;
		this.height = 0;
		this.mimeType = "";
		this.error = true;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getMimeType() {
		return mimeType;
	}

	public boolean isError() {
		return error;
	}

}
