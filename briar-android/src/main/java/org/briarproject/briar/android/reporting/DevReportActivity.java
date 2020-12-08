package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.logout.HideUiActivity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.view.View.INVISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;
import static org.briarproject.briar.android.reporting.BriarReportCollector.IS_FEEDBACK;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class DevReportActivity extends AppCompatActivity {

	private ReportViewModel viewModel;
	private ProgressBar progressBar;
	private boolean isFeedback = true;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);
		setContentView(R.layout.activity_dev_report);

		viewModel = new ViewModelProvider(this).get(ReportViewModel.class);
		viewModel.setCrashReportIntent(getIntent());

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		progressBar = findViewById(R.id.progressBar);

		viewModel.getCrashReportData().observe(this, crashReportData -> {
			isFeedback = (boolean) crashReportData.get(IS_FEEDBACK);

			String title = getString(isFeedback ? R.string.feedback_title :
					R.string.crash_report_title);
			requireNonNull(getSupportActionBar()).setTitle(title);

			displayFragment(isFeedback);
		});
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Localizer.getInstance().setLocale(base));
	}

	@Override
	public void onBackPressed() {
		closeReport();
	}

	void displayFragment(boolean showReportForm) {
		progressBar.setVisibility(INVISIBLE);
		Fragment f;
		if (showReportForm) {
			f = ReportFormFragment.newInstance(isFeedback);
			requireNonNull(getSupportActionBar()).show();
		} else {
			f = new CrashFragment();
			requireNonNull(getSupportActionBar()).hide();
		}
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f, f.getTag())
				.commit();
	}

	void closeReport() {
		viewModel.cancelReports();
		exit();
	}

	void exit() {
		if (!isFeedback) {
			Intent i = new Intent(this, HideUiActivity.class);
			i.addFlags(FLAG_ACTIVITY_NEW_TASK
					| FLAG_ACTIVITY_NO_ANIMATION
					| FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(i);
		}
		finish();
	}

}
