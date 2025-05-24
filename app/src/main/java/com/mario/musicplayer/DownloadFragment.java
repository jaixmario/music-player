public class DownloadFragment extends Fragment {

    private EditText urlInput;
    private Button downloadButton;
    private ProgressBar progressBar;
    private Context context;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download, container, false);

        context = requireContext();
        urlInput = view.findViewById(R.id.urlInput);
        downloadButton = view.findViewById(R.id.downloadButton);
        progressBar = view.findViewById(R.id.downloadProgress);

        progressBar.setVisibility(View.GONE);

        downloadButton.setOnClickListener(v -> {
            String ytUrl = urlInput.getText().toString().trim();
            if (ytUrl.isEmpty()) {
                Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show();
            } else {
                downloadMusicFromApi(ytUrl);
            }
        });

        return view;
    }

    private void downloadMusicFromApi(String ytUrl) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String apiUrl = "https://f7bba52b-4af0-4efa-9b26-23a593b1826b-00-hxlccw5fjxp5.pike.replit.dev/download?url=" + URLEncoder.encode(ytUrl, "UTF-8");
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    File musicDir = new File(Environment.getExternalStorageDirectory(), "Music/mario");
                    if (!musicDir.exists()) musicDir.mkdirs();

                    String fileName = "song_" + System.currentTimeMillis() + ".mp3";
                    File outFile = new File(musicDir, fileName);

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out = new FileOutputStream(outFile);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }

                    out.flush();
                    out.close();
                    in.close();

                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(context, "Saved to /Music/mario", Toast.LENGTH_LONG).show();
                    });
                } else {
                    showError("Failed to download");
                }

                conn.disconnect();
            } catch (Exception e) {
                showError("Error: " + e.getMessage());
            }
        }).start();
    }

    private void showError(String msg) {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        });
    }
}