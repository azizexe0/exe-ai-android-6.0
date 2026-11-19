# EXE AI 6.0

EXE AI 5.0 Android Java projesi temel alınarak 6.0 araştırma ve otomatik öğrenme güncellemesi hazırlanmıştır.

## 6.0 yenilikleri
- Önceki SQLite hafızası, TSV bilgi paketi, sesli giriş, TTS, kamera OCR ve uygulama başlatma özellikleri korunmuştur.
- Bilinmeyen sorular için otomatik web araştırması eklenmiştir.
- DuckDuckGo HTML ve Bing RSS arama sonuçları birlikte taranır; erişilebilen sonuç sayfalarından metin çıkarılır.
- Araştırma sonuçları SQLite öğrenilmiş bilgi tablosuna kaydedilir ve kaynak URL'leri cevapta gösterilir.
- Web araması için OpenAI API anahtarı gerekmez.
- Sık kullanılan bazı Türkçe kısaltmalar ve ağır yazım hataları web aramasından önce normalize edilir.

## Önemli sınırlar
- Bu sürüm gerçek ChatGPT modeli içermez. API'siz arama + sayfa okuma + yerel hafıza sistemi kullanır; kendi başına genel amaçlı LLM gibi yeni metin akıl yürütmesi yapmaz.
- İnternetteki her siteye erişim garanti değildir. CAPTCHA, robots.txt, oturum açma, JavaScript zorunluluğu ve sunucu engelleri nedeniyle bazı sayfalar okunamayabilir.
- Sürekli mikrofon dinleme, güç tuşuna üç kez basmayı sistem genelinde yakalama ve başka uygulamaların üstünde Google Asistan benzeri panel bu ZIP'e eklenmemiştir. Android bu işlevleri normal uygulamalara genel olarak açmaz; ayrı izinler/cihaz desteği ve farklı bir mimari gerekir.
- WhatsApp mesaj gönderme kullanıcı onayı olmadan otomatikleştirilmez.

## Derleme
GitHub'a yükleyip Actions sekmesinden Build EXE AI 6.0 APK workflow'unu çalıştır.
