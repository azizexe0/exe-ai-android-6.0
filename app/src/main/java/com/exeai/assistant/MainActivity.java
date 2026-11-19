package com.exeai.assistant;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int SPEECH = 41;
    private static final int IMAGE_PICK = 42;
    private static final int CAMERA = 43;
    private static final int IMPORT_QA = 44;

    private static final int RED = Color.rgb(255, 45, 55);
    private LinearLayout chat;
    private EditText input;
    private ScrollView scroll;
    private DB db;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private boolean ttsReady = false;
    private boolean voiceEnabled = true;
    private String selectedVoiceName = "";
    private float speechRate = 1.0f;
    private float speechPitch = 1.0f;
    private String lastImageText = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 9, 9));
        getWindow().setNavigationBarColor(Color.rgb(9, 9, 9));

        prefs = getSharedPreferences("exe_ai_settings", MODE_PRIVATE);
        voiceEnabled = prefs.getBoolean("voice_enabled", true);
        selectedVoiceName = prefs.getString("voice_name", "");
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        speechPitch = prefs.getFloat("speech_pitch", 1.0f);

        db = new DB(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("tr", "TR"));
                applySavedVoice();
            }
        });

        buildUI();
        addMessage("EXE AI 6.0 hazır. Çok kaynaklı web araştırması, kalıcı öğrenme, yazım hatası toleransı, sesli giriş, kamera OCR ve uygulama komutları kullanılabilir.\nGeliştiricim: Aziz EXE.", false);
    }

    private void buildUI() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackgroundColor(Color.rgb(10, 10, 10));

        TextView title = new TextView(this);
        title.setText("EXE AI 6.0");
        title.setTextColor(RED);
        title.setTextSize(26);
        title.setTypeface(null, 1);
        title.setPadding(18, 18, 12, 3);

        TextView sub = new TextView(this);
        sub.setText("PERSONAL AI ASSISTANT  •  RED / BLACK");
        sub.setTextColor(0xffaaaaaa);
        sub.setTextSize(10);
        sub.setPadding(20, 0, 12, 12);
        base.addView(title);
        base.addView(sub);

        scroll = new ScrollView(this);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(12, 10, 12, 10);
        scroll.addView(chat);
        base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(6, 4, 6, 4);
        addTool(tools, "🎙 Sesli", v -> listen());
        addTool(tools, "📷 Kamera", v -> openCamera());
        addTool(tools, "🖼 Görsel", v -> pickImage());
        addTool(tools, "⚙ Ayarlar", v -> showSettings());
        base.addView(tools);

        LinearLayout extraTools = new LinearLayout(this);
        extraTools.setPadding(6, 0, 6, 4);
        addTool(extraTools, "📚 Bilgi paketi", v -> importKnowledge());
        addTool(extraTools, "▶ YouTube", v -> launchApp("youtube"));
        addTool(extraTools, "⛏ Minecraft", v -> launchApp("minecraft"));
        addTool(extraTools, "⚙ Sistem", v -> openSettings());
        base.addView(extraTools);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(8, 8, 8, 12);
        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setHint("EXE AI'ye yaz...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xff888888);
        input.setPadding(12, 10, 12, 10);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(RED));
        bottom.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button send = new Button(this);
        send.setText("GÖNDER");
        send.setTextColor(Color.WHITE);
        send.setAllCaps(false);
        send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb00020));
        send.setOnClickListener(v -> send());
        bottom.addView(send);
        base.addView(bottom);

        setContentView(base);
    }

    private void addTool(LinearLayout row, String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(2, 2, 2, 2);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff242424));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(2, 0, 2, 0);
        row.addView(b, lp);
    }

    private void addMessage(String message, boolean user) {
        TextView t = new TextView(this);
        t.setText((user ? "SEN  ›  " : "EXE  ›  ") + message);
        t.setTextColor(user ? 0xffffb5b5 : Color.WHITE);
        t.setTextSize(15);
        t.setPadding(14, 12, 14, 12);
        t.setBackgroundColor(user ? 0xff351316 : 0xff202020);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 5, 0, 5);
        chat.addView(t, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void send() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        input.setText("");
        addMessage(q, true);
        hideKeyboard();

        String learningQuery = getLearningQuery(q);
        if (learningQuery != null) {
            if (learningQuery.isEmpty()) {
                reply("Öğrenmek istediğin konuyu komuttan sonra yaz. Örnek: *** RAM nedir?");
                return;
            }
            addMessage("İnternette ücretsiz kaynak aranıyor...", false);
            worker.execute(() -> learnFromInternet(learningQuery));
            return;
        }

        String answer = answer(q);
        if (answer == null) {
            if (isOnlineResearchAvailable()) {
                worker.execute(() -> researchAndLearn(q));
            } else {
                reply("Bunu biraz daha açar mısın? Elimdeki çevrimdışı bilgilerle daha iyi yardımcı olayım.");
            }
        } else {
            reply(answer);
        }
    }

    // Supported formats: "*** konu", "### konu", and also "***konu" / "###konu".
    private String getLearningQuery(String q) {
        String trimmed = q.trim();
        if (trimmed.startsWith("***") || trimmed.startsWith("###")) {
            return trimmed.substring(3).trim();
        }
        String lower = trimmed.toLowerCase(new Locale("tr", "TR"));
        if (lower.startsWith("öğren:") || lower.startsWith("ogren:")) {
            return trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }
        return null;
    }

    private void learnFromInternet(String query) {
        researchAndLearn(query);
    }

    private boolean isOnlineResearchAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void researchAndLearn(String rawQuery) {
        try {
            String query = TextUnderstanding.prepareSearchQuery(rawQuery);
            WebResearcher.Result result = WebResearcher.research(query);
            if (result == null || result.answer.trim().isEmpty()) {
                postReply("Bunu biraz daha açar mısın? Hangi yönünü merak ettiğini söylersen daha iyi yardımcı olabilirim.");
                return;
            }
            db.saveLearned(rawQuery, result.answer, result.sources);
            postReply(result.answer + "\n\nKaynaklar:\n" + result.sources);
        } catch (Exception ignored) {
            postReply("Bunu biraz daha açar mısın? Böylece doğru bilgiye daha kolay ulaşabilirim.");
        }
    }

    private String safeError(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private void postReply(String text) {
        mainHandler.post(() -> reply(text));
    }

    private void reply(String text) {
        addMessage(text, false);
        if (tts != null && ttsReady && voiceEnabled) {
            tts.setSpeechRate(speechRate);
            tts.setPitch(speechPitch);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "exe_reply");
        }
    }

    private String answer(String q) {
        String s = TextUnderstanding.normalize(q);
        if (TextUnderstanding.containsIntent(s, "seni kim yapti", "seni kim kodladi", "sen kimsin", "ai sen kimsin"))
            return "Ben EXE AI'yım. Beni Aziz EXE kodladı!";
        if (TextUnderstanding.containsIntent(s, "adin ne", "ismin ne", "senin adin ne"))
            return "Benim adım EXE AI. Kişisel yapay zekâ asistanınım.";
        if (TextUnderstanding.containsIntent(s, "merhaba", "selam", "slm", "iyi gunler", "gunaydin"))
            return "Merhaba! Yazım hatası olsa da ne demek istediğini anlamaya çalışıyorum. Bugün ne yapalım?";
        if (TextUnderstanding.containsIntent(s, "nasilsin", "naber", "ne haber"))
            return "İyiyim, teşekkür ederim! Seninle sohbet etmeye hazırım. Sen nasılsın?";
        if (TextUnderstanding.containsIntent(s, "ne yapiyorsun", "napion", "napıyorsun"))
            return "Mesajını anlamaya ve yardımcı olmaya çalışıyorum. İstersen bir görev de verebilirsin.";
        if (TextUnderstanding.containsIntent(s, "tesekkur", "sagol", "eyvallah"))
            return "Rica ederim! Başka bir konuda da yardımcı olabilirim.";
        if (TextUnderstanding.containsIntent(s, "saat kac", "saat kacta"))
            return "Cihaz saati: " + new SimpleDateFormat("HH:mm", new Locale("tr", "TR")).format(new Date());

        if (TextUnderstanding.containsIntent(s, "komik", "espiri yap", "saka yap", "beni guldur")) {
            prefs.edit().putBoolean("comedy_mode", true).apply();
            return nextJoke();
        }
        if (prefs.getBoolean("comedy_mode", false) && TextUnderstanding.containsIntent(s, "devam", "baska", "daha")) return nextJoke();
        if (TextUnderstanding.containsIntent(s, "espiri modu kapat", "normal moda gec")) {
            prefs.edit().putBoolean("comedy_mode", false).apply();
            return "Espiri modunu kapattım. Normal sohbete döndüm.";
        }

        // Any installed app can be launched by its visible name, with approximate spelling.
        if (hasOpenVerb(s)) {
            String app = launchInstalledAppFromText(q);
            if (app != null) return app + " uygulamasını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "youtube")) { launchApp("youtube"); return "YouTube'u açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "minecraft")) { launchApp("minecraft"); return "Minecraft'ı açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "chrome")) { launchApp("chrome"); return "Chrome'u açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "wifi", "wi fi")) {
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)); return "Wi-Fi ayarlarını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "bluetooth")) {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); return "Bluetooth ayarlarını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "yardim", "help", "neler yapabilirsin"))
            return "Komutlar: *** konu — internetten öğren; Sesli — konuş; Kamera — fotoğraf OCR; Bilgi paketi — TSV içe aktar; uygulama adı + aç — uygulama başlat; *komik* — espiri modu; devam — yeni espiri.";

        String learned = db.findLearnedFuzzy(q);
        if (learned != null) return "Hafızamda kayıtlı yakın eşleşme:\n" + learned;
        String qa = db.knowledgeFuzzy(q);
        if (qa != null) return qa;
        if (!lastImageText.isEmpty() && TextUnderstanding.containsIntent(s, "resim", "fotograf", "gorsel", "kamerada"))
            return "Fotoğraftan okuyabildiğim yazılar:\n" + lastImageText;
        return null;
    }

    private boolean hasOpenVerb(String s) {
        return TextUnderstanding.containsIntent(s, "ac", "baslat", "calistir", "gir", "uygulamasini ac", "programi ac");
    }

    private String launchInstalledAppFromText(String original) {
        String[] words = TextUnderstanding.normalize(original).split("\\s+");
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(query, 0);
        ResolveInfo best = null;
        int bestScore = Integer.MAX_VALUE;
        for (ResolveInfo info : apps) {
            CharSequence labelCs = info.loadLabel(getPackageManager());
            if (labelCs == null) continue;
            String label = TextUnderstanding.normalize(labelCs.toString());
            if (normalizedContainsAppAlias(words, label)) { best = info; bestScore = 0; break; }
            for (int i = 0; i < words.length; i++) {
                for (int n = 1; n <= 4 && i + n <= words.length; n++) {
                    StringBuilder phrase = new StringBuilder();
                    boolean commandWord = false;
                    for (int j = i; j < i + n; j++) {
                        String w = words[j];
                        if (TextUnderstanding.containsIntent(w, "ac", "baslat", "calistir", "uygulama", "programi", "gir", "lutfen")) { commandWord = true; break; }
                        if (phrase.length() > 0) phrase.append(' ');
                        phrase.append(w);
                    }
                    if (commandWord || phrase.length() == 0) continue;
                    String candidate = phrase.toString();
                    int distance = TextUnderstanding.distance(candidate, label);
                    if (label.contains(candidate) || candidate.contains(label)) distance = 0;
                    int allowed = Math.max(1, Math.min(2, label.length() / 6));
                    if (distance <= allowed && distance < bestScore) { bestScore = distance; best = info; }
                }
            }
        }
        if (best == null) return null;
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        try { startActivity(launch); return best.loadLabel(getPackageManager()).toString(); }
        catch (Exception e) { return null; }
    }

    private boolean normalizedContainsAppAlias(String[] words, String label) {
        String joined = String.join(" ", words);
        if (joined.contains(label)) return true;
        if (label.contains("youtube") && (joined.contains(" yt ") || joined.startsWith("yt "))) return true;
        if (label.contains("minecraft") && (joined.contains(" mc ") || joined.startsWith("mc "))) return true;
        if ((label.contains("settings") || label.contains("ayarlar")) && joined.contains("ayarlar")) return true;
        return false;
    }

    private String nextJoke() {
        String[] jokes = {
                "Bilgisayar neden doktora gitmiş? Çünkü virüs kapmış! 😂",
                "Matematik kitabı neden üzgünmüş? Çünkü çok fazla problemi varmış! 😄",
                "Telefon neden gözlük takmış? Çünkü rehberindeki isimleri daha net görmek istemiş! 🤓",
                "Programcı neden denize girmemiş? Çünkü çok fazla bug varmış! 🐛",
                "Wi-Fi neden saklambaçta iyiymiş? Çünkü herkes onu arıyormuş! 😂"
        };
        int index = prefs.getInt("joke_index", 0);
        prefs.edit().putInt("joke_index", index + 1).apply();
        return jokes[index % jokes.length];
    }

    private void launchApp(String name) {
        String[] packages;
        if (name.equals("youtube")) packages = new String[]{"com.google.android.youtube"};
        else if (name.equals("minecraft")) packages = new String[]{"com.mojang.minecraftpe", "com.mojang.minecrafttrialpe"};
        else packages = new String[]{"com.android.chrome"};

        for (String p : packages) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) {
                startActivity(i);
                return;
            }
        }
        Toast.makeText(this, "Uygulama bulunamadı veya kurulu değil.", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "EXE AI'ye konuş");
        try {
            startActivityForResult(i, SPEECH);
        } catch (Exception e) {
            addMessage("Bu cihazda konuşma tanıma servisi bulunamadı.", false);
        }
    }

    private void openCamera() {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (i.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(i, CAMERA);
        } else {
            addMessage("Bu cihazda kamera uygulaması bulunamadı.", false);
        }
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(i, IMAGE_PICK);
    }

    private void processImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            runOcr(image);
        } catch (Exception e) {
            addMessage("Görsel açılamadı: " + safeError(e), false);
        }
    }

    private void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            addMessage("Kameradan fotoğraf alınamadı. Tekrar deneyebilirsin.", false);
            return;
        }
        runOcr(InputImage.fromBitmap(bitmap, 0));
    }

    private void runOcr(InputImage image) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    lastImageText = result.getText() == null ? "" : result.getText().trim();
                    if (lastImageText.isEmpty()) {
                        addMessage("Fotoğrafta okunabilir yazı bulamadım. Bu sürüm fotoğraflardaki yazıları okuyabilir.", false);
                    } else {
                        addMessage("Fotoğraftan okuduğum yazılar:\n" + lastImageText
                                + "\n\nŞimdi bu yazılar hakkında soru sorabilirsin.", false);
                    }
                    recognizer.close();
                })
                .addOnFailureListener(e -> {
                    addMessage("Görsel okuma başarısız: " + safeError(e), false);
                    recognizer.close();
                });
    }

    private void importKnowledge() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, IMPORT_QA);
        } catch (Exception e) {
            addMessage("Dosya seçici açılamadı.", false);
        }
    }

    private void importTsv(Uri uri) {
        worker.execute(() -> {
            int imported = 0;
            int skipped = 0;
            SQLiteDatabase writable = db.getWritableDatabase();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                if (in == null) throw new IllegalStateException("Dosya okunamadı");
                writable.beginTransaction();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\t", 3);
                    if (parts.length < 2) {
                        skipped++;
                        continue;
                    }
                    String question = parts[0].trim();
                    String answer = parts[1].trim();
                    String category = parts.length >= 3 ? parts[2].trim() : "genel";
                    if (question.isEmpty() || answer.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    db.putQa(writable, question, answer, category);
                    imported++;
                }
                writable.setTransactionSuccessful();
            } catch (Exception e) {
                postReply("Bilgi paketi içe aktarılamadı: " + safeError(e));
                return;
            } finally {
                if (writable.inTransaction()) writable.endTransaction();
            }
            final int ok = imported;
            final int bad = skipped;
            postReply("Bilgi paketi tamamlandı. Eklenen/güncellenen soru: " + ok + ". Atlanan satır: " + bad
                    + ". Veriler kalıcı SQLite hafızasına kaydedildi.");
        });
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(22, 12, 22, 4);

        CheckBox voiceCheck = new CheckBox(this);
        voiceCheck.setText("EXE AI sesli cevap versin");
        voiceCheck.setTextColor(Color.WHITE);
        voiceCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(RED));
        voiceCheck.setChecked(voiceEnabled);
        panel.addView(voiceCheck);

        TextView voiceLabel = new TextView(this);
        voiceLabel.setText("Ses seçimi (cihazda yüklü sesler)");
        voiceLabel.setTextColor(Color.LTGRAY);
        voiceLabel.setPadding(0, 12, 0, 4);
        panel.addView(voiceLabel);

        List<Voice> available = getAvailableVoices();
        List<String> names = new ArrayList<>();
        names.add("Varsayılan Türkçe sesi");
        for (Voice v : available) names.add(v.getName() + " (" + v.getLocale().toLanguageTag() + ")");
        Spinner voiceSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        voiceSpinner.setAdapter(adapter);
        int selected = 0;
        for (int x = 0; x < available.size(); x++) {
            if (available.get(x).getName().equals(selectedVoiceName)) selected = x + 1;
        }
        voiceSpinner.setSelection(selected);
        panel.addView(voiceSpinner);

        TextView rateLabel = new TextView(this);
        rateLabel.setText("Konuşma hızı");
        rateLabel.setTextColor(Color.LTGRAY);
        rateLabel.setPadding(0, 12, 0, 2);
        panel.addView(rateLabel);
        SeekBar rateBar = new SeekBar(this);
        rateBar.setMax(15);
        rateBar.setProgress(Math.max(0, Math.min(15, Math.round((speechRate - 0.5f) * 10))));
        panel.addView(rateBar);

        TextView pitchLabel = new TextView(this);
        pitchLabel.setText("Ses tonu");
        pitchLabel.setTextColor(Color.LTGRAY);
        pitchLabel.setPadding(0, 8, 0, 2);
        panel.addView(pitchLabel);
        SeekBar pitchBar = new SeekBar(this);
        pitchBar.setMax(15);
        pitchBar.setProgress(Math.max(0, Math.min(15, Math.round((speechPitch - 0.5f) * 10))));
        panel.addView(pitchBar);

        new AlertDialog.Builder(this)
                .setTitle("EXE AI • Ses ayarları")
                .setView(panel)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    voiceEnabled = voiceCheck.isChecked();
                    speechRate = 0.5f + rateBar.getProgress() / 10.0f;
                    speechPitch = 0.5f + pitchBar.getProgress() / 10.0f;
                    selectedVoiceName = voiceSpinner.getSelectedItemPosition() == 0
                            ? "" : available.get(voiceSpinner.getSelectedItemPosition() - 1).getName();
                    prefs.edit()
                            .putBoolean("voice_enabled", voiceEnabled)
                            .putString("voice_name", selectedVoiceName)
                            .putFloat("speech_rate", speechRate)
                            .putFloat("speech_pitch", speechPitch)
                            .apply();
                    applySavedVoice();
                    if (!voiceEnabled && tts != null) tts.stop();
                    Toast.makeText(this, "Ses ayarları kaydedildi.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private List<Voice> getAvailableVoices() {
        List<Voice> result = new ArrayList<>();
        if (tts == null || !ttsReady || tts.getVoices() == null) return result;
        Set<Voice> all = tts.getVoices();
        for (Voice v : all) {
            if (v.getLocale() != null && "tr".equalsIgnoreCase(v.getLocale().getLanguage())) result.add(v);
        }
        if (result.isEmpty()) result.addAll(all);
        return result;
    }

    private void applySavedVoice() {
        if (tts == null || !ttsReady) return;
        tts.setSpeechRate(speechRate);
        tts.setPitch(speechPitch);
        if (!selectedVoiceName.isEmpty() && tts.getVoices() != null) {
            for (Voice v : tts.getVoices()) {
                if (v.getName().equals(selectedVoiceName)) {
                    try {
                        tts.setVoice(v);
                    } catch (Exception ignored) { }
                    break;
                }
            }
        } else {
            tts.setLanguage(new Locale("tr", "TR"));
        }
    }

    private void hideKeyboard() {
        try {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == SPEECH) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                input.setText(results.get(0));
                send();
            }
        } else if (requestCode == IMAGE_PICK) {
            if (data.getData() != null) processImage(data.getData());
        } else if (requestCode == CAMERA) {
            Bundle extras = data.getExtras();
            if (extras != null && extras.get("data") instanceof Bitmap) {
                processBitmap((Bitmap) extras.get("data"));
            } else {
                addMessage("Kamera fotoğrafı alınamadı. Bu kamera uygulaması küçük önizleme döndürmüyor olabilir.", false);
            }
        } else if (requestCode == IMPORT_QA) {
            if (data.getData() != null) importTsv(data.getData());
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    static class DB extends SQLiteOpenHelper {
        DB(Context context) {
            super(context, "exe_ai_memory.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, fact TEXT NOT NULL, tag TEXT, created INTEGER)");
            d.execSQL("CREATE TABLE qa(question_key TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, category TEXT)");
            d.execSQL("CREATE TABLE learned(question_key TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, source TEXT, created INTEGER)");
            d.execSQL("CREATE INDEX idx_qa_category ON qa(category)");
            d.execSQL("CREATE INDEX idx_memory_created ON memory(created)");
            seed(d);
        }

        @Override
        public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
            // Preserve the earlier v1 memory and QA rows while adding indexed keys.
            if (oldVersion < 2) {
                d.execSQL("CREATE TABLE IF NOT EXISTS learned(question_key TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, source TEXT, created INTEGER)");
                boolean hasKey = false;
                boolean hasCategory = false;
                try (Cursor c = d.rawQuery("PRAGMA table_info(qa)", null)) {
                    while (c.moveToNext()) {
                        String column = c.getString(1);
                        if ("question_key".equals(column)) hasKey = true;
                        if ("category".equals(column)) hasCategory = true;
                    }
                }
                if (!hasKey) d.execSQL("ALTER TABLE qa ADD COLUMN question_key TEXT");
                if (!hasCategory) d.execSQL("ALTER TABLE qa ADD COLUMN category TEXT");
                d.execSQL("UPDATE qa SET question_key=lower(trim(question)) WHERE question_key IS NULL OR question_key=''");
                d.execSQL("DELETE FROM qa WHERE rowid NOT IN (SELECT MIN(rowid) FROM qa GROUP BY question_key)");
                d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_qa_question_key ON qa(question_key)");
            }
        }

        static String key(String text) {
            return text == null ? "" : text.trim().toLowerCase(new Locale("tr", "TR")).replaceAll("\\s+", " ");
        }

        void saveMemory(String fact, String tag) {
            ContentValues v = new ContentValues();
            v.put("fact", fact);
            v.put("tag", tag);
            v.put("created", System.currentTimeMillis());
            getWritableDatabase().insert("memory", null, v);
        }

        void saveLearned(String question, String answer, String source) {
            ContentValues v = new ContentValues();
            v.put("question_key", key(question));
            v.put("question", question);
            v.put("answer", answer);
            v.put("source", source);
            v.put("created", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("learned", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }

        String findLearned(String question) {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT answer FROM learned WHERE question_key=?",
                    new String[]{key(question)});
            try { return c.moveToFirst() ? c.getString(0) : null; }
            finally { c.close(); }
        }

        String knowledge(String question) {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT answer FROM qa WHERE question_key=?",
                    new String[]{key(question)});
            try { return c.moveToFirst() ? c.getString(0) : null; }
            finally { c.close(); }
        }

        String findLearnedFuzzy(String question) {
            String exact = findLearned(question);
            if (exact != null) return exact;
            String target = TextUnderstanding.normalize(question);
            Cursor c = getReadableDatabase().rawQuery("SELECT question, answer FROM learned LIMIT 500", null);
            try {
                while (c.moveToNext()) {
                    String candidate = TextUnderstanding.normalize(c.getString(0));
                    if (TextUnderstanding.containsIntent(target, candidate) || TextUnderstanding.containsIntent(candidate, target)) return c.getString(1);
                }
            } finally { c.close(); }
            return null;
        }

        String knowledgeFuzzy(String question) {
            String exact = knowledge(question);
            if (exact != null) return exact;
            String target = TextUnderstanding.normalize(question);
            Cursor c = getReadableDatabase().rawQuery("SELECT question, answer FROM qa LIMIT 2000", null);
            try {
                while (c.moveToNext()) {
                    String candidate = TextUnderstanding.normalize(c.getString(0));
                    if (TextUnderstanding.containsIntent(target, candidate) || TextUnderstanding.containsIntent(candidate, target)) return c.getString(1);
                }
            } finally { c.close(); }
            return null;
        }

        void putQa(SQLiteDatabase d, String question, String answer, String category) {
            ContentValues v = new ContentValues();
            v.put("question_key", key(question));
            v.put("question", question);
            v.put("answer", answer);
            v.put("category", category);
            d.insertWithOnConflict("qa", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }

        void seed(SQLiteDatabase d) {
            putQa(d, "ram nedir", "RAM, cihazda çalışan uygulamaların kullandığı geçici bellektir.", "elektronik");
            putQa(d, "cpu nedir", "CPU, komutları işleyen ana işlemci birimidir.", "elektronik");
            putQa(d, "gpu nedir", "GPU, grafik işlemlerini hızlandıran işlemci birimidir.", "elektronik");
            putQa(d, "html nedir", "HTML, web sayfalarının yapısını tanımlayan işaretleme dilidir.", "kodlama");
            putQa(d, "java nedir", "Java, Android uygulamaları da geliştirilebilen bir programlama dilidir.", "kodlama");
            putQa(d, "minecraft bedrock nedir", "Minecraft Bedrock, birçok farklı platformda bulunan Minecraft sürümüdür.", "minecraft");
            putQa(d, "redstone nedir", "Redstone, Minecraft'ta devreler ve otomasyon mekanizmaları kurmaya yarar.", "minecraft");
            putQa(d, "internet nedir", "İnternet, dünya çapındaki bilgisayar ağlarını birbirine bağlayan küresel ağdır.", "genel");
            putQa(d, "yapay zeka nedir", "Yapay zekâ, bilgisayarların öğrenme ve dil işleme gibi görevleri yapmasını sağlayan yöntemler alanıdır.", "bilim");
        }
    }
}
