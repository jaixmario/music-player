public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<File> songList;
    private MediaPlayer mediaPlayer;
    private String[] items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        listView = findViewById(R.id.listViewSongs);
        checkPermission();

        songList = findSongs(new File("/storage/emulated/0/Music/"));
        items = new String[songList.size()];

        for (int i = 0; i < songList.size(); i++) {
            items[i] = songList.get(i).getName().replace(".mp3", "").replace(".wav", "");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (mediaPlayer != null) mediaPlayer.stop();

            mediaPlayer = MediaPlayer.create(getApplicationContext(), Uri.fromFile(songList.get(i)));
            mediaPlayer.start();
        });
    }

    private ArrayList<File> findSongs(File root) {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = root.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    songs.addAll(findSongs(file));
                } else if (file.getName().endsWith(".mp3") || file.getName().endsWith(".wav")) {
                    songs.add(file);
                }
            }
        }

        return songs;
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
    }
}