package com.apps.adrcotfas.goodtime.Backup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.apps.adrcotfas.goodtime.Database.AppDatabase;
import com.apps.adrcotfas.goodtime.R;
import com.apps.adrcotfas.goodtime.Session;
import com.apps.adrcotfas.goodtime.Statistics.SessionViewModel;
import com.apps.adrcotfas.goodtime.Util.FileUtils;
import com.apps.adrcotfas.goodtime.databinding.DialogBackupBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;

import static android.app.Activity.RESULT_OK;
import static com.apps.adrcotfas.goodtime.Util.StringUtils.formatDateAndTime;

public class BackupFragment extends BottomSheetDialogFragment {
    private static final int IMPORT_BACKUP_REQUEST = 0;

    private SessionViewModel mSessionViewModel;
    private Runnable exportRunnable;

    public BackupFragment() {
        // Empty constructor required for DialogFragment
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        DialogBackupBinding binding = DataBindingUtil.inflate(inflater, R.layout.dialog_backup, container, false);

        mSessionViewModel = ViewModelProviders.of(this).get(SessionViewModel.class);
        binding.exportBackup.setOnClickListener(view -> exportBackup());
        binding.importBackup.setOnClickListener(view -> importBackup());
        binding.exportCsv.setOnClickListener(view -> exportCsv());

        View view = binding.getRoot();
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORT_BACKUP_REQUEST && data != null) {
            Uri uri = data.getData();
            if (uri != null && resultCode == RESULT_OK) {
                onImportBackupResult(uri);
            }
        }
    }

    private void importBackup() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Import backup?")
                .setMessage("The current entries will be lost.")
                .setPositiveButton("OK", (dialog, id) -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, IMPORT_BACKUP_REQUEST);
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel())
                .show();
    }

    private void exportBackup() {
        Handler handler = new Handler();

        LiveData<List<Session>> sessionsLiveData = mSessionViewModel.getAllSessionsByEndTime();
        sessionsLiveData.observe(this, sessions -> {
            if (sessions.isEmpty()) {
                handler.post(() -> Toast.makeText(getActivity(), "There are no completed sessions", Toast.LENGTH_SHORT).show());
                dismiss();
            } else {
                exportRunnable = () -> {
                    AppDatabase.closeInstance();
                    File file = getActivity().getDatabasePath("goodtime-db");
                    File destinationPath = new File(getActivity().getFilesDir(), "tmp");
                    File destinationFile = new File(destinationPath, "Goodtime-Backup-" + formatDateAndTime(System.currentTimeMillis()));
                    destinationFile.deleteOnExit();
                    if (file.exists()) {
                        try {
                            FileUtils.copyFile(file, destinationFile);
                            if (destinationFile.exists()) {
                                Uri fileUri = FileProvider.getUriForFile(getActivity(), "com.apps.adrcotfas.goodtime", destinationFile);
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_SEND);
                                intent.setType("application/zip");
                                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                getActivity().startActivity(Intent.createChooser(intent, "Export backup"));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // run this on the UI thread
                        handler.post(() -> {
                            sessionsLiveData.removeObservers(BackupFragment.this);
                            dismiss();
                        });
                    }
                };
                Thread t = new Thread(exportRunnable);
                t.start();
                exportRunnable = null;
            }
        });
    }

    private void exportCsv() {
        Handler handler = new Handler();

        LiveData<List<Session>> sessionsLiveData = mSessionViewModel.getAllSessionsByEndTime();
        sessionsLiveData.observe(this, sessions -> {
            if (sessions.isEmpty()) {
                handler.post(() -> Toast.makeText(getActivity(), "There are no completed sessions", Toast.LENGTH_SHORT).show());
                dismiss();
            } else {
                exportRunnable = () -> {
                    try {
                        File destinationPath = new File(getActivity().getFilesDir(), "tmp");
                        File output = new File(destinationPath, "Goodtime-CSV-" + formatDateAndTime(System.currentTimeMillis()));
                        output.deleteOnExit();

                        if (!output.getParentFile().exists())
                            output.getParentFile().mkdirs();

                        if (!output.exists()) {
                            output.createNewFile();
                        }

                        FileOutputStream fos = new FileOutputStream(output);
                        // write header
                        fos.write("time-of-completion".getBytes());
                        fos.write(",".getBytes());
                        fos.write("duration".getBytes());
                        fos.write(",".getBytes());
                        fos.write("label".getBytes());
                        fos.write("\n".getBytes());
                        for (Session s : sessions) {
                            fos.write(formatDateAndTime(s.endTime).getBytes());
                            fos.write(",".getBytes());
                            fos.write(Long.toString(s.totalTime).getBytes());
                            fos.write(",".getBytes());
                            fos.write(s.label != null ? s.label.getBytes() : "".getBytes());
                            fos.write("\n".getBytes());
                        }
                        fos.flush();
                        fos.close();
                        if (output.exists()) {
                            Uri fileUri = FileProvider.getUriForFile(BackupFragment.this.getActivity(), "com.apps.adrcotfas.goodtime", output);
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_SEND);
                            intent.setType("text/csv");
                            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            BackupFragment.this.getActivity().startActivity(Intent.createChooser(intent, "Export CSV backup"));

                            // run this on the UI thread
                            handler.post(() -> {
                                sessionsLiveData.removeObservers(BackupFragment.this);
                                dismiss();
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                Thread t = new Thread(exportRunnable);
                t.start();
                exportRunnable = null;
            }
        });
    }

    private void onImportBackupResult(Uri uri) {
        new ImportBackupTask(mSessionViewModel.getApplication()).execute(uri);
    }
}