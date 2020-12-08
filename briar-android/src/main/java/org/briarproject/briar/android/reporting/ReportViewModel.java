package org.briarproject.briar.android.reporting;

import android.app.Application;
import android.content.Intent;

import org.acra.data.CrashReportData;
import org.acra.dialog.CrashReportDialogHelper;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.acra.interaction.DialogInteraction.EXTRA_REPORT_FILE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ReportViewModel extends AndroidViewModel {

	private static final Logger LOG =
			getLogger(ReportViewModel.class.getName());

	private CrashReportDialogHelper helper;
	private File reportFile;
	private final MutableLiveData<CrashReportData> crashReportData =
			new MutableLiveData<>();

	public ReportViewModel(@NonNull Application application) {
		super(application);
	}

	void setCrashReportIntent(Intent intent) {
		helper = new CrashReportDialogHelper(getApplication(), intent);
		reportFile = (File) intent.getSerializableExtra(EXTRA_REPORT_FILE);

		// TODO different async mechanism
		new Thread(() -> {
			try {
				crashReportData.postValue(helper.getReportData());
			} catch (IOException e) {
				// TODO
				LOG.log(WARNING, "Could not load report file", e);
			}
		}).start();
	}

	void sendCrashReport(String comment, String email) {
		helper.sendCrash(comment, email);
	}

	void cancelReports() {
		helper.cancelReports();
	}

	LiveData<CrashReportData> getCrashReportData() {
		return crashReportData;
	}

	/**
	 * Available after {@link #getCrashReportData()} published its first value.
	 */
	public File getReportFile() {
		return reportFile;
	}

}
