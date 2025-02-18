package com.game3.voicecontrolledgame;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private int screenWidth;
    private int screenHeight;

    private SurfaceView gameSurface;
    private SurfaceHolder surfaceHolder;
    private Paint paint;
    private int characterY = 1500;
    private int characterX = 150;
    private float voiceStrength = 0f;

    private int score = 0;
    private boolean isPlaying = true;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private Handler gameHandler = new Handler();
    private Runnable gameLoop;

    private TextView scoreText;
    private Button btnRestart;

    private List<Platform> platforms = new ArrayList<>();
    private Platform standingPlatform;
    private Handler scoreHandler = new Handler(Looper.getMainLooper());
    private Runnable scoreRunnable;
    private float platformSpeed = 0f; // Initial platform speed
    private final float maxPlatformSpeed = 60f; // Maximum platform speed
    private final int maxScore = 1200; // Score at which max speed is reached
    private boolean isOnPlatform;
    private float characterVelocity = 0f; // Vertical velocity
    private float gravity = 6f;  // Gravity acceleration
    private final float jumpMultiplier = 23f; // Adjust jump strength
    // Base gravity value
    private final float maxGravity = 2f ;  // Maximum gravity cap
    private Bitmap[] characterRunFrames, characterJumpFrames;
    private Bitmap platformBitmap;
    private int frameIndex = 0;
    private long lastFrameTime = 0;
    private int frameDelay = 100;
    int characterWidth = 200; // Set your desired width
    int characterHeight = 230; // Set your desired height


    private static class Platform {
        int x, y, width, height;

        Platform(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
    private void getScreenDimensions() {
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (!isListening) { // Prevent multiple calls
                startListening();
            }
        } else {
            Toast.makeText(this, "Microphone permission is required for this game", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getScreenDimensions();

        gameSurface = findViewById(R.id.gameSurface);
        surfaceHolder = gameSurface.getHolder();
        paint = new Paint();

        scoreText = findViewById(R.id.scoreText);
        btnRestart = findViewById(R.id.btnRestart);

        characterRunFrames = new Bitmap[]{
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char1), characterWidth, characterHeight, true),
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char2), characterWidth, characterHeight, true),
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char3), characterWidth, characterHeight, true),
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char4), characterWidth, characterHeight, true)
        };

        characterJumpFrames = new Bitmap[]{
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char_jump), characterWidth, characterHeight, true),
                Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.char_land), characterWidth, characterHeight, true)
        };

        platformBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.platform), characterWidth, characterHeight, true);





        // Check microphone permissions
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognizer", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                voiceStrength = Math.max(0, rmsdB); // Ensure non-negative values

                // Normalize voice strength to screen height
                int minHeight = 200;  // Minimum height character can reach
                int maxHeight = screenHeight - 200; // Maximum height

                // Map voice strength (rmsdB) to height
                float normalizedStrength = Math.min(voiceStrength / 13f, 1f); // Scale rmsdB to 0-1
                characterY = (int) (maxHeight - (normalizedStrength * (maxHeight - minHeight)));

                // Ensure it doesn’t go below the ground
                if (characterY > screenHeight - 80) {
                    characterY = screenHeight - 100;
                }
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                startListening();
            }

            @Override
            public void onError(int error) {
                Log.e("SpeechRecognizer", "Error: " + error);
                startListening();
            }

            @Override
            public void onResults(Bundle results) {
                Log.d("SpeechRecognizer", "Results received");
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        if (speechRecognizer == null) {
            Toast.makeText(this, "SpeechRecognizer not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRestart.setOnClickListener(v -> restartGame());
        startGame();
    }

    private void startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        // Set up the intent for recognizing speech
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // Restart listening if needed, even during silence
        speechRecognizer.startListening(intent);
    }
/**
    private void updatePlatformSpeed() {
        // Gradually increase speed based on the score
        platformSpeed = 15f + (maxPlatformSpeed - 10f) * Math.min(score / (float) maxScore, 1f);
        gravity = 1f + (maxGravity - .1f) * Math.min(score / (float) maxScore, 1f);
        gravity = Math.min(gravity, maxGravity);
    }
**/

private boolean checkLeftSideCollision(Platform platform) {
    // Check if the character is to the left of the platform
    int characterLeft = characterX - 190;  // left edge of the character
    int characterRight = characterX + 190; // right edge of the character
    int characterTop = characterY - 40;   // top of the character
    int characterBottom = characterY + 40; // bottom of the character

    // Ensure the character is moving toward the platform (coming from the left)
    return characterRight > platform.x && characterLeft < platform.x &&
            characterBottom > platform.y && characterTop < platform.y + platform.height;
}
    private void startGame() {
        resetGame(); // Clear all previous data before starting
        // startScoring(); // Score
        startListening(); // Start voice recognition

        // Create the initial platform
        int initialPlatformWidth = 400;
        int initialPlatformHeight = 100 + screenHeight;
        int initialPlatformX = 80; // Align with character's X position
        int initialPlatformY = characterY + 50; // Slightly below the character
        platforms.add(new Platform(initialPlatformX, initialPlatformY, initialPlatformWidth, initialPlatformHeight));

        // Create the initial standing platform
        int standingPlatformWidth =  screenWidth;
        int standingPlatformHeight = 1 + screenHeight;
        int standingPlatformX = 0;  // Align with character's X position
        int standingPlatformY = characterY + 50;  // Slightly below the character's starting position
        standingPlatform = new Platform(standingPlatformX, standingPlatformY, standingPlatformWidth, standingPlatformHeight);


        gameLoop = new Runnable() {
            @Override
            public void run() {
                if (!isPlaying) return;

                Canvas canvas = null;
                try {
                    if (surfaceHolder.getSurface().isValid()) {
                        canvas = surfaceHolder.lockCanvas();
                        if (canvas != null) {
                            canvas.drawColor(Color.WHITE); // Clear the canvas

                            if (!isOnPlatform) {
                                characterVelocity += gravity;  // Apply gravity
                                characterY += characterVelocity;
                            }

                            if (characterY >= screenHeight) { // Adjust this value based on your screen size
                                isPlaying = false; // Stop the game
                                btnRestart.setVisibility(Button.VISIBLE); // Show restart button
                            }

                            // Check for collisions with platforms
                            for (Platform platform : platforms) {
                                if (checkLeftSideCollision(platform)) {
                                    // Move the character back if it is approaching from the left side of the platform
                                    characterX = platform.x - characterX; // Move the character to the left edge of the platform
                                    characterVelocity = 0;
                                }



                                int characterBottom = characterY + 50;
                                if (characterY + characterVelocity >= platform.y - 50 &&
                                        characterVelocity > 0 &&  // Ensure it's falling down
                                        characterX + 50 >= platform.x && characterX <= platform.x + platform.width) {

                                    characterY = platform.y - 50;
                                    characterVelocity = 0;
                                    isOnPlatform = true;
                                    score += 10; // Add score when landing on a platform
                                    scoreText.setText("Score: " + score);
                                }


                            }
                            for (Platform platform : platforms) {
                                platform.x -= 5; // Move left
                            }

                            // Remove platforms that have moved off-screen
                            platforms.removeIf(platform -> platform.x + platform.width < 0);

                            // Generate new platforms if needed
                            if (!platforms.isEmpty() && platforms.get(platforms.size() - 1).x < gameSurface.getWidth() - 300) {
                                generateRandomPlatform();
                            }

                            Bitmap currentFrame;
                            if (!isOnPlatform) {
                                // Character is in the air → use jump or land frame
                                currentFrame = (characterVelocity < 0) ? characterJumpFrames[0] : characterJumpFrames[1];
                            } else {
                                // Character is on the ground → use running animation
                                if (System.currentTimeMillis() - lastFrameTime > frameDelay) {
                                    frameIndex = (frameIndex + 1) % characterRunFrames.length;
                                    lastFrameTime = System.currentTimeMillis();
                                }
                                currentFrame = characterRunFrames[frameIndex];
                            }

                            // Draw the selected frame
                            canvas.drawBitmap(currentFrame, characterX, characterY - 168, null);

                            // Draw Standing Platform
                            paint.setColor(Color.BLACK);
                           // canvas.drawRect(standingPlatform.x, standingPlatform.y, standingPlatform.x + standingPlatform.width, standingPlatform.y + standingPlatform.height, paint);

                            // Draw Platforms
                            paint.setColor(Color.RED);
                            for (Platform platform : platforms) {
                                canvas.drawRect(platform.x, platform.y, platform.x + platform.width, platform.y + platform.height, paint);
                            }

                            //updatePlatformSpeed(); // Adjust speed based on the new score
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas); // Unlock the canvas
                    }
                }

                gameHandler.postDelayed(this, 20); // Continue the game loop
            }
        };

        gameHandler.post(gameLoop); // Start the game loop
    }

    private void generateRandomPlatform() {
        Random random = new Random();

        int platformHeight = 1 + screenHeight;
        int platformWidth = random.nextInt(200) + 180;

        // Y pos
        int platformY;
        if (score >= 600) {
            // Choose between front of character and slightly above
            int choice = random.nextInt(3);
            if (choice == 0) {
                platformY = standingPlatform.y - 100; // Front of the character
            } else if (choice == 1) {
                platformY = standingPlatform.y - 150; // Mid
            } else {
                platformY = standingPlatform.y - 300; // Higher
            }
        } else if (score >= 300) {
            platformY = random.nextBoolean()
                    ? standingPlatform.y - 100 // Front of character
                    : standingPlatform.y - 300; // Slightly above character
        } else {
            // Always generate in front of the character initially
            platformY = standingPlatform.y - 100;
        }

        // X pos
        int platformX = platforms.isEmpty()
                ? 700
                : platforms.get(platforms.size() - 1).x + platforms.get(platforms.size() - 1).width;

        int spacing = Math.min(900, 450 + (int)(score * 0.5)); // Increase spacing slightly as score increases
        platformX += spacing;

        platforms.add(new Platform(platformX, platformY, platformWidth, platformHeight));
    }

    private void startScoring() {
        scoreRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    score = 0; // Increment score by 1
                    scoreText.setText("Score: " + score); // Update the score display

                    // Schedule the next score increment after 1 second
                    scoreHandler.postDelayed(this, 100);
                }
            }
        };
        scoreHandler.post(scoreRunnable); // Start the scoring
    }


    private void stopScoring() {
        if (scoreRunnable != null) {
            scoreHandler.removeCallbacks(scoreRunnable); // Stop the scoring updates
        }
    }

    private void resetGame() {
        // Clear all previous handlers to prevent conflicts
        gameHandler.removeCallbacks(gameLoop);

        // Clear all platforms
        platforms.clear();

        // Reset game variables
        score = 0;
        isPlaying = true;
        characterY = 1500;
        characterVelocity = 0;
        isOnPlatform = true;

        // Hide the restart button
        btnRestart.setVisibility(Button.GONE);

        // Clear the SurfaceView
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(Color.WHITE); // Clear the canvas
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void restartGame() {
        isPlaying = false;
        gameHandler.removeCallbacks(gameLoop);
        stopScoring();

        // Clear all platforms and reset character position
        platforms.clear();
        score = 0;
        characterY = standingPlatform.y + 50;
        characterVelocity = 0;
        gravity = 6f;
        isOnPlatform = true;

        // Hide restart button
        btnRestart.setVisibility(Button.GONE);

        // Ensure the game surface is cleared
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(Color.WHITE);
            surfaceHolder.unlockCanvasAndPost(canvas);
        }

        // Restart game logic
        isPlaying = true;
        startGame();
    }


    private void endGame() {
        isPlaying = false;  // Stop the game
        btnRestart.setVisibility(Button.VISIBLE);  // Show the restart button
        speechRecognizer.stopListening();  // Stop listening for voice commands
        stopScoring();
        Toast.makeText(this, "Game Over", Toast.LENGTH_SHORT).show();  // Show a "Game Over" message


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}