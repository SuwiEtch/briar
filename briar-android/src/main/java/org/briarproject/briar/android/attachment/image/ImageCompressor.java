package org.briarproject.briar.android.attachment.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import androidx.annotation.VisibleForTesting;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.BitmapFactory.decodeStream;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_IMAGE_SIZE;

public class ImageCompressor {

	private static Logger LOG =
			getLogger(ImageCompressor.class.getName());

	private ImageSizeCalculator imageSizeCalculator;

	public ImageCompressor(ImageSizeCalculator imageSizeCalculator) {
		this.imageSizeCalculator = imageSizeCalculator;
	}

	@VisibleForTesting
	public InputStream compressImage(InputStream is, String contentType,
			int maxSize)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			Bitmap bitmap = createBitmap(is, contentType, maxSize);
			for (int quality = 100; quality >= 0; quality -= 10) {
				if (!bitmap.compress(JPEG, quality, out))
					throw new IOException();
				if (out.size() <= MAX_IMAGE_SIZE) {
					if (LOG.isLoggable(INFO)) {
						LOG.info("Compressed image to "
								+ out.size() + " bytes, quality " + quality);
					}
					return new ByteArrayInputStream(out.toByteArray());
				}
				out.reset();
			}
			throw new IOException();
		} finally {
			tryToClose(is, LOG, WARNING);
		}
	}

	private Bitmap createBitmap(InputStream is, String contentType, int maxSize)
			throws IOException {
		is = new BufferedInputStream(is);
		Size size = imageSizeCalculator.getSize(is, contentType);
		if (size.error) throw new IOException();
		if (LOG.isLoggable(INFO))
			LOG.info("Original image size: " + size.width + "x" + size.height);
		int dimension = Math.max(size.width, size.height);
		int inSampleSize = 1;
		while (dimension > maxSize) {
			inSampleSize *= 2;
			dimension /= 2;
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Scaling attachment by factor of " + inSampleSize);
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = inSampleSize;
		if (contentType.equals("image/png"))
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		Bitmap bitmap = decodeStream(is, null, options);
		if (bitmap == null) throw new IOException();
		return bitmap;
	}

}
