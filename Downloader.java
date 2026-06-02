package com.worker;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Downloader extends TelegramLongPollingBot {

    private final Map<Long, String> userStates = new HashMap<>();

    @Override
    public String getBotUsername() {
        return System.getenv("BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String user = update.getMessage().getText().trim();
            long chatid = update.getMessage().getChatId();
            String name = update.getMessage().getFrom().getFirstName();

            if (user.equals("/start")) {
                userStates.remove(chatid);
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatid));
                message.setText("Hello " + name + "\nWelcome to SkyDownloader 👋🏻\nChoose an option from the menu below:");

                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                keyboardMarkup.setSelective(true);
                keyboardMarkup.setResizeKeyboard(true);
                keyboardMarkup.setOneTimeKeyboard(false);

                List<KeyboardRow> keyboard = new ArrayList<>();
                KeyboardRow row = new KeyboardRow();
                row.add(new KeyboardButton("📹 Download Video"));
                row.add(new KeyboardButton("🎵 Download Music"));
                keyboard.add(row);

                keyboardMarkup.setKeyboard(keyboard);
                message.setReplyMarkup(keyboardMarkup);

                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (user.equals("📹 Download Video")) {
                userStates.put(chatid, "VIDEO_MODE");
                sendTextMessage(chatid, "Please send me a link from Instagram, TikTok, Facebook, or YouTube!");
                return;
            }

            if (user.equals("🎵 Download Music")) {
                userStates.put(chatid, "MUSIC_MODE");
                sendTextMessage(chatid, "Please type the song name or artist you want to search!");
                return;
            }

            String currentState = userStates.getOrDefault(chatid, "");
            String lowerUser = user.toLowerCase();

            if (currentState.equals("VIDEO_MODE")) {
                if (lowerUser.contains("instagram.com") || lowerUser.contains("youtube.com") ||
                        lowerUser.contains("youtu.be") || lowerUser.contains("tiktok.com") || lowerUser.contains("facebook.com")) {

                    sendTextMessage(chatid, "wait for download⏳");

                    new Thread(() -> {
                        try {
                            File downloadDir = new File("/tmp/downloads");
                            if (!downloadDir.exists()) downloadDir.mkdirs();

                            String outfile = "/tmp/downloads/video_" + System.currentTimeMillis() + ".mp4";
                            ProcessBuilder process = new ProcessBuilder("yt-dlp", "-f", "b", "-o", outfile, user);
                            process.redirectErrorStream(true);
                            Process proc = process.start();
                            int exitCode = proc.waitFor();

                            if (exitCode == 0) {
                                File downloadedFile = new File(outfile);
                                if (downloadedFile.exists()) {
                                    SendVideo sendVideo = new SendVideo();
                                    sendVideo.setChatId(String.valueOf(chatid));
                                    sendVideo.setVideo(new InputFile(downloadedFile));
                                    execute(sendVideo);
                                    downloadedFile.delete();

                                    if (lowerUser.contains("youtube.com") || lowerUser.contains("youtu.be")) {
                                        try {
                                            String audioFile = "/tmp/downloads/audio_" + System.currentTimeMillis() + ".mp3";
                                            ProcessBuilder audioProcess = new ProcessBuilder("yt-dlp", "-x", "--audio-format", "mp3", "--audio-quality", "320", "-o", audioFile, user);
                                            audioProcess.redirectErrorStream(true);
                                            if (audioProcess.start().waitFor() == 0) {
                                                File aFile = new File(audioFile);
                                                if (aFile.exists()) {
                                                    SendAudio sendAudio = new SendAudio();
                                                    sendAudio.setChatId(String.valueOf(chatid));
                                                    sendAudio.setAudio(new InputFile(aFile));
                                                    execute(sendAudio);
                                                    aFile.delete();
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else {
                                    sendTextMessage(chatid, "file not found");
                                }
                            } else {
                                sendTextMessage(chatid, "api dont work");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            sendTextMessage(chatid, "wrong " + e.getMessage());
                        }
                    }).start();
                } else {
                    sendTextMessage(chatid, "wrong link! Please send a valid link for video download.");
                }

            } else if (currentState.equals("MUSIC_MODE")) {
                if (user.length() < 2) {
                    sendTextMessage(chatid, "wrong name");
                    return;
                }

                sendTextMessage(chatid, "wait for download⏳");

                new Thread(() -> {
                    try {
                        File downloadDir = new File("/tmp/downloads");
                        if (!downloadDir.exists()) downloadDir.mkdirs();

                        ProcessBuilder getTitleProcess = new ProcessBuilder(
                                "yt-dlp",
                                "--get-title",
                                "ytsearch1:" + user
                        );
                        Process pTitle = getTitleProcess.start();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(pTitle.getInputStream()));
                        String fullTitle = reader.readLine();
                        pTitle.waitFor();
                        if (fullTitle == null || fullTitle.trim().isEmpty()) {
                            fullTitle = user;
                        }

                        String songTitle = fullTitle;
                        String artistName = "SkyDownloader";

                        if (fullTitle.contains(" - ")) {
                            String[] parts = fullTitle.split(" - ", 2);
                            artistName = parts[0].trim();
                            songTitle = parts[1].trim();
                        } else if (fullTitle.contains("-")) {
                            String[] parts = fullTitle.split("-", 2);
                            artistName = parts[0].trim();
                            songTitle = parts[1].trim();
                        }

                        String safeTitle = songTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                        String searchAudioFile = "/tmp/downloads/" + safeTitle + ".mp3";
                        File sFile = new File(searchAudioFile);

                        ProcessBuilder searchProcess = new ProcessBuilder(
                                "yt-dlp",
                                "-x",
                                "--audio-format", "mp3",
                                "--audio-quality", "320",
                                "-o", sFile.getAbsolutePath(),
                                "ytsearch1:" + user
                        );
                        searchProcess.redirectErrorStream(true);

                        if (searchProcess.start().waitFor() == 0) {
                            if (sFile.exists()) {
                                SendAudio sendAudio = new SendAudio();
                                sendAudio.setChatId(String.valueOf(chatid));
                                sendAudio.setAudio(new InputFile(sFile));
                                sendAudio.setTitle(songTitle);
                                sendAudio.setPerformer(artistName);
                                execute(sendAudio);
                                sFile.delete();
                            } else {
                                sendTextMessage(chatid, "file not found");
                            }
                        } else {
                            sendTextMessage(chatid, "api dont work");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendTextMessage(chatid, "wrong " + e.getMessage());
                    }
                }).start();

            } else {
                sendTextMessage(chatid, "Please select an option from the menu first (Video or Music).");
            }
        }
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
