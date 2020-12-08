package org.briarproject.briar.android.reporting;

import android.content.Context;

import org.acra.config.CoreConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;
import org.briarproject.briar.android.BriarApplication;

import androidx.annotation.NonNull;

public class BriarReportSenderFactory implements ReportSenderFactory {

	@NonNull
	@Override
	public ReportSender create(@NonNull Context ctx,
			@NonNull CoreConfiguration config) {
		// ACRA passes in a JobSenderService (extends ContextWrapper) as context
		BriarApplication app = (BriarApplication) ctx.getApplicationContext();
		return new BriarReportSender(app.getApplicationComponent());
	}
}
