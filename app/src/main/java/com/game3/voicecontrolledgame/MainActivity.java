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
    private float gravity = 2.5f;  // Gravity acceleration
    private final float jumpMultiplier = 23f; // Adjust jump strength

    private final float maxGravity = 2f ;  // Maximum gravity cap
    private Bitmap[] characterRunFrames, characterJumpFrames;
    private Bitmap platformBitmap;
    private int frameIndex = 0;
    private long lastFrameTime = 0;
    private int frameDelay = 100;
    int characterWidth = 200; // Set your desired width
    int characterHeight = 230; // Set your desired height
    private Platform lastPlatform = null;
    private boolean canJump = true; // Cooldown flag
    private Bitmap characterBitmap;

    // Hitbox
    int hitboxWidth = characterWidth - 45;  // Adjust width
    int hitboxHeight = characterHeight - 50; // Adjust height
    int hitboxX = characterX + 10; // Center hitbox inside sprite
    int hitboxY = characterY - 135; // Adjust vertical alignment


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
        platformBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.platform);




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
                voiceStrength = Math.max(0, rmsdB); // Ensure non-negative value
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


    private void startGame() {
        resetGame(); // Clear all previous data before starting
        startListening(); // Start voice recognition

        // Create the initial platform
        int initialPlatformWidth = 400;
        int initialPlatformHeight = 100 + screenHeight;
        int initialPlatformX = 80; // Align with character's X position
        int initialPlatformY = characterY + 50; // Slightly below the character
        platforms.add(new Platform(initialPlatformX, initialPlatformY, initialPlatformWidth, initialPlatformHeight));

        // Create the initial standing platform
        int standingPlatformWidth = screenWidth;
        int standingPlatformHeight = 1 + screenHeight;
        int standingPlatformX = 0;
        int standingPlatformY = characterY + 50;
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

                            // Handle Jumping (Prevent Double Jump)
                            if (voiceStrength > 9 && isOnPlatform && canJump) {
                                isOnPlatform = false;
                                canJump = false;
                                characterVelocity = -Math.min(voiceStrength * 5.5f, 45);

                                // Delay before allowing another jump
                                new Handler().postDelayed(() -> canJump = true, 300);
                            }

                            // Apply Gravity Smoothly
                            characterVelocity += gravity;
                            hitboxY += characterVelocity;

                            // Prevent Falling Through the Bottom of the Screen
                            if (hitboxY >= screenHeight) {
                                isPlaying = false;
                                btnRestart.setVisibility(Button.VISIBLE);
                            }




                            // Check for Collisions with Platforms
                            for (Platform platform : platforms) {
                                int hitboxBottom = hitboxY + hitboxHeight;
                                boolean isAbovePlatform = hitboxY < platform.y;
                                boolean isWithinPlatformWidth = hitboxX + hitboxWidth >= platform.x && hitboxX <= platform.x + platform.width;

                                // **LANDING CHECK (Using Hitbox Instead of Bitmap)**
                                if (hitboxBottom >= platform.y && isAbovePlatform && isWithinPlatformWidth) {
                                    if (!isOnPlatform) { // Score only when transitioning from air to platform
                                        score += 10;
                                        scoreText.setText("Score: " + score);
                                        lastPlatform = platform;
                                    }
                                    isOnPlatform = true;
                                    hitboxY = platform.y - hitboxHeight; // Align hitbox on top
                                    characterVelocity = 0;
                                }

                                boolean hitsLeftSide = hitboxX + hitboxWidth > platform.x
                                        && hitboxX < platform.x
                                        && hitboxY + hitboxHeight > platform.y
                                        && hitboxY < platform.y + platform.height;

                                boolean hitsRightSide = hitboxX < platform.x + platform.width
                                        && hitboxX + hitboxWidth > platform.x + platform.width
                                        && hitboxY + hitboxHeight > platform.y
                                        && hitboxY < platform.y + platform.height;

                                if (hitsLeftSide || hitsRightSide) {
                                    hitboxX = (hitsLeftSide) ? platform.x - hitboxWidth : platform.x + platform.width;
                                }
                            }

                            // If no platform was landed on, keep falling
                            if (!isOnPlatform) {
                                characterVelocity += gravity;
                                hitboxY += characterVelocity;
                            }


                            // Move Platforms Left
                            for (Platform platform : platforms) {
                                platform.x -= 15f + (maxPlatformSpeed - 10f) * Math.min(score / (float) maxScore, 1f);;

                            }

                            // Remove Platforms that Move Off-Screen
                            platforms.removeIf(platform -> platform.x + platform.width < 0);

                            // Generate New Platforms
                            if (!platforms.isEmpty() && platforms.get(platforms.size() - 1).x < gameSurface.getWidth() - 300) {
                                generateRandomPlatform();
                            }

                            // Select Character Sprite Based on State
                            Bitmap currentFrame;
                            if (!isOnPlatform) {
                                currentFrame = (characterVelocity < 0) ? characterJumpFrames[0] : characterJumpFrames[1];
                            } else {
                                if (System.currentTimeMillis() - lastFrameTime > frameDelay) {
                                    frameIndex = (frameIndex + 1) % characterRunFrames.length;
                                    lastFrameTime = System.currentTimeMillis();
                                }
                                currentFrame = characterRunFrames[frameIndex];
                            }

                            // Draw Character

                            canvas.drawBitmap(currentFrame, hitboxX - 10, hitboxY - 35,null);

                            // Draw Hitbox (DEBUGGING)
                            paint.setColor(Color.BLUE); // Change to any color you like
                            paint.setStyle(Paint.Style.STROKE); // Outline instead of fill
                            paint.setStrokeWidth(5); // Thickness of the hitbox line
                            canvas.drawRect(hitboxX, hitboxY, hitboxX + hitboxWidth, hitboxY + hitboxHeight, paint);

                            // Draw Platforms
                            paint.setColor(Color.RED);
                            for (Platform platform : platforms) {
                                canvas.drawRect(platform.x, platform.y, platform.x + platform.width, platform.y + platform.height, paint);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }

                gameHandler.postDelayed(this, 20);
            }
        };

        gameHandler.post(gameLoop);
    }


    private void generateRandomPlatform() {
        Random random = new Random();

        int platformHeight = 1 + screenHeight;
        int platformWidth = random.nextInt(200) + 180;

        // Y pos
        int platformY;
        if (score >= 150) {
            // Choose between front of character and slightly above
            int choice = random.nextInt(3);
            if (choice == 0) {
                platformY = standingPlatform.y - 100; // Front of the character
            } else if (choice == 1) {
                platformY = standingPlatform.y - 150; // Mid
            } else {
                platformY = standingPlatform.y - 300; // Higher
            }
        } else if (score >= 50) {
            platformY = random.nextBoolean()
                    ? standingPlatform.y - 100 // Front of character
                    : standingPlatform.y - 300; // Slightly above character
        } else {
            // Always generate in front of the character initially
            platformY = standingPlatform.y - random.nextInt(150) + 70;
        }

        // X pos
        int platformX = platforms.isEmpty()
                ? 700
                : platforms.get(platforms.size() - 1).x + platforms.get(platforms.size() - 1).width;

        int spacing = Math.min(400, 450 + (int)(score * 0.5)); // Increase spacing slightly as score increases
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
        // Stop the current game loop
        isPlaying = false;
        gameHandler.removeCallbacks(gameLoop);

        // Reset game variables
        score = 0;
        scoreText.setText("Score: " + score);
        characterVelocity = 0;
        gravity = 2.5f;
        isOnPlatform = true;

        // Reset character & hitbox positions
        characterX = 150;  // Set this to your original starting X position
        characterY = 1500; // Original starting Y position
        hitboxX = characterX + 10;
        hitboxY = characterY - 135; // Adjust to your original hitbox placement

        // Clear platforms and re-add the starting platform
        platforms.clear();
        int initialPlatformWidth = 400;
        int initialPlatformHeight = 100 + screenHeight;
        int initialPlatformX = 80;
        int initialPlatformY = characterY + 50;
        platforms.add(new Platform(initialPlatformX, initialPlatformY, initialPlatformWidth, initialPlatformHeight));

        // Hide restart button
        btnRestart.setVisibility(Button.GONE);

        // Ensure the game surface is cleared
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(Color.WHITE);
            surfaceHolder.unlockCanvasAndPost(canvas);
        }

        // Restart game logic (Prevent double loops)
        new Handler().postDelayed(() -> {
            if (!isPlaying) {
                isPlaying = true;
                startGame();
            }
        }, 100); // Small delay to ensure reset completes properly
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