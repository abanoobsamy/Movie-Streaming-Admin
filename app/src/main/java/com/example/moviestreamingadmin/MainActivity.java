package com.example.moviestreamingadmin;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.example.moviestreamingadmin.Model.VideoUploadDetails;
import com.example.moviestreamingadmin.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import org.apache.commons.io.FilenameUtils;

import java.io.FilenameFilter;
import java.net.FileNameMap;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private Uri videoUri;
    private String videoCategory, videoTitle, currentUid;

    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private StorageTask storageTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        databaseReference = FirebaseDatabase.getInstance().getReference().child("Videos");
        storageReference = FirebaseStorage.getInstance().getReference().child("Videos");

        setSpinnerCategoryList();

        binding.btnUploadVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                openFileVideo();
            }
        });

        binding.btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                uploadFileToFirebase();
            }
        });
    }

    private void uploadFileToFirebase() {

        if (binding.tvVideosSelected.equals("No Videos Selected")) {

            Toast.makeText(this, "Please select an video!", Toast.LENGTH_SHORT).show();
        }
        else {

            if (storageTask != null && storageTask.isInProgress()) {

                Toast.makeText(this, "Video uploads are already in progress...", Toast.LENGTH_SHORT).show();
            }
            else {

                uploadFiles();
            }
        }
    }

    private void openFileVideo() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        openFileVideo.launch(intent);
    }

    private void setSpinnerCategoryList() {

        List<String> categories = new ArrayList<>();

        categories.add("Action");
        categories.add("Adventure");
        categories.add("War");
        categories.add("Comedy");
        categories.add("Sports");
        categories.add("Romantic");

        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinner.setAdapter(adapter);

        binding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                videoCategory = adapterView.getItemAtPosition(i).toString();

                Toast.makeText(getApplicationContext(), "Select: " + videoCategory, Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    //Instead of startActivityForResult / OnActivityResult...
    ActivityResultLauncher<Intent> openFileVideo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult()
            , new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {

            Intent data = result.getData();

            if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {

                videoUri = data.getData();

                /**
                 * To Get Data Videos From Media
                 */
                String [] projection = {MediaStore.MediaColumns.DATA,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Thumbnails.DATA};

                Cursor cursor = MainActivity.this.getContentResolver()
                        .query(videoUri, projection, null, null,
                                MediaStore.Video.Media.DEFAULT_SORT_ORDER);

                int columnIndexData = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

                while (cursor.moveToNext()) {

                    String path = cursor.getString(columnIndexData);
                    videoTitle = FilenameUtils.getBaseName(path);

                }

                binding.tvVideosSelected.setText(videoTitle);
            }
        }
    });

    private void uploadFiles() {

        if (videoUri != null) {

            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setCancelable(false);
            dialog.setMessage("Video Uploading...");
            dialog.show();

            StorageReference fileReference = storageReference
                    .child(System.currentTimeMillis() + "." + getFileExtension(videoUri)); //manuel

            storageTask = fileReference.putFile(videoUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                            fileReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {

                                    String videoUrl = uri.toString();
                                    String videoDesc = binding.etDescMovie.getText().toString();
                                    String videoTitle = binding.etTitleMovie.getText().toString();

                                    //videoCategory which in onItemSelected for Spinner.
                                    VideoUploadDetails videoUploadDetails =
                                            new VideoUploadDetails("", "", ""
                                                    , videoUrl, videoTitle, videoDesc, videoCategory);

                                    String uploadId = databaseReference.push().getKey();
                                    databaseReference.child(uploadId).setValue(videoUploadDetails);
                                    currentUid = uploadId;

                                    dialog.dismiss();

                                    if (currentUid.equals(uploadId)) {

                                        startUploadThumbnailActivity();
                                    }
                                }
                            });
                        }
                    }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {

                            double progress = (100.0 * snapshot.getBytesTransferred() / snapshot.getTotalByteCount());
                            dialog.setMessage("Uploaded " + ((int) progress) + "%...");
                        }
                    });
        }
        else {

            Toast.makeText(this, "No video selected to upload!", Toast.LENGTH_SHORT).show();
        }
    }

    private void startUploadThumbnailActivity() {

        Intent intent = new Intent(MainActivity.this, UploadThumbnailActivity.class);
        intent.putExtra("currentUid", currentUid);
        intent.putExtra("thumbnailsName", videoTitle);
        startActivity(intent);

        Toast.makeText(getApplicationContext(),
                "Video Uploaded Successfully, Let's Upload Video Thumbnail.", Toast.LENGTH_SHORT).show();
    }

    private String getFileExtension(Uri uri) {

        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();

        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }
}