package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.util.Log;

import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.briarproject.bramble.api.reporting.DevReporter;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.briar.android.AndroidComponent;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;

import javax.inject.Inject;

import androidx.annotation.NonNull;

import static org.acra.ReportField.REPORT_ID;

public class BriarReportSender implements ReportSender {

	private final AndroidComponent component;

	@Inject
	DevReporter reporter;

	BriarReportSender(AndroidComponent component) {
		this.component = component;
	}

	@Override
	public void send(@NonNull Context ctx,
			@NonNull CrashReportData errorContent)
			throws ReportSenderException {
		component.inject(this);
		try {
			String crashReport = errorContent.toJSON();
			File reportDir = AndroidUtils.getReportDir(ctx);
			String reportId = errorContent.getString(REPORT_ID);
			Log.e("TEST", "id: " + reportId);
			Log.e("TEST", "crashReport: " + crashReport);
			reporter.encryptReportToFile(reportDir, reportId, crashReport);
		} catch (FileNotFoundException | JSONException e) {
			throw new ReportSenderException("Failed to encrypt report", e);
		}
	}
}
