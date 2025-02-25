package com.game3.voicecontrolledgame;

import android.Manifest;
import android.app.MediaRouteButton;
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
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
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

    private float voiceStrength = 0f;

    private int score = 0;
    private boolean isPlaying = true;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private Handler gameHandler = new Handler();
    private Runnable gameLoop;
    private List<Platform> platforms = new ArrayList<>();
    private Platform standingPlatform;
    private Handler scoreHandler = new Handler(Looper.getMainLooper());
    private Runnable scoreRunnable;
    private float platformSpeed = 0f; // Initial platform speed
    private final float maxPlatformSpeed = 50f; // Maximum platform speed
    private final int maxScore = 1200; // Score at which max speed is reached
    private boolean isOnPlatform;
    private float characterVelocity = 0f; // Vertical velocity
    private float gravity = 2.4f;  // Gravity acceleration
    private final float jumpMultiplier = 23f; // Adjust jump strength

    private final float maxGravity = 2f ;  // Maximum gravity cap
    private Bitmap[] characterRunFrames, characterJumpFrames;
    private Bitmap platformBitmap;
    private int frameIndex = 0;
    private long lastFrameTime = 0;
    private int frameDelay = 100;
    private Platform lastPlatform = null;
    private boolean canJump = true;
    private Bitmap characterBitmap, backgroundBitmap;

    // Hitbox
    private int characterY = 1510;
    private int characterX = 150;
    int characterWidth = 180; // Set your desired width
    int characterHeight = 225; // Set your desired height
    int hitboxWidth = characterWidth - 35;  // Adjust width
    int hitboxHeight = characterHeight - 45; // Adjust height
    int hitboxX = characterX + 25; // Center hitbox inside sprite
    int hitboxY = characterY - 135; // Adjust vertical alignment
    private int backgroundX = 0;
    long startTime = System.currentTimeMillis();

    // Menus
    Button btnResume, btnRestart;
    RelativeLayout pauseMenu, gameOverMenu;
    private TextView scoreText, finalScoreText;
    boolean isPaused = false;


    private static class Platform {
        public Bitmap scaledBitmap;
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

        ImageButton btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnRestart = findViewById(R.id.btnRestart);
        pauseMenu = findViewById(R.id.pauseMenu);
        gameOverMenu = findViewById(R.id.gameOverMenu);

        gameSurface = findViewById(R.id.gameSurface);
        surfaceHolder = gameSurface.getHolder();
        paint = new Paint();

        scoreText = findViewById(R.id.scoreText);
        finalScoreText = findViewById(R.id.finalScoreText);

        btnPause.setOnClickListener(v -> {
            isPaused = true;
            pauseGame();
        });

        btnResume.setOnClickListener(v -> {
            isPaused = false;
            resumeGame();
        });

        btnRestart.setOnClickListener(v -> {
            restartGame();
        });

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

        platformBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.platform);


        backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
        backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, screenWidth, screenHeight, true);


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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isOnPlatform && canJump) {  // Ensure the character is only allowed to jump when touching a platform
                isOnPlatform = false;
                canJump = false; // Prevent additional jumps until landing
                characterVelocity = -50; // Jump height
            }
        }
        return super.onTouchEvent(event);
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

    private void startGame() {
        resetGame(); // Clear all previous data before starting
        startListening(); // Start voice recognition

        // Create the initial platform
        int initialPlatformWidth = 400;
        int initialPlatformHeight = screenHeight - 1;
        int initialPlatformX = 80; // Align with character's X position
        int initialPlatformY = characterY + 50; // Slightly below the character
        platforms.add(new Platform(initialPlatformX, initialPlatformY, initialPlatformWidth, initialPlatformHeight));

        // Create the initial standing platform
        int standingPlatformWidth = 1;
        int standingPlatformHeight = 1;
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
                            if (voiceStrength > 9.99 && isOnPlatform) { // Remove 'canJump'
                                isOnPlatform = false;
                                characterVelocity = -Math.min(voiceStrength * 5.5f, 45);
                            }

                            // Apply Gravity Smoothly
                            characterVelocity += gravity;
                            hitboxY += characterVelocity;

                            // Prevent Falling Through the Bottom of the Screen
                            if (hitboxY >= screenHeight) {
                                isPlaying = false;
                                gameOver();
                            }


                            // Check for Collisions with Platforms
                            for (Platform platform : platforms) {
                                int hitboxBottom = hitboxY + hitboxHeight;
                                boolean isAbovePlatform = hitboxY < platform.y;
                                boolean isWithinPlatformWidth = hitboxX + hitboxWidth * 0.8f >= platform.x && hitboxX + hitboxWidth * 0.2f <= platform.x + platform.width;

                                // **LANDING CHECK (Prevent Snapping from Side)**
                                if (platform.x + platform.width > 0) {
                                    if (hitboxBottom >= platform.y && isAbovePlatform && isWithinPlatformWidth) {
                                        if (!isOnPlatform) { // Score only when transitioning from air to platform
                                            score += 10;
                                            scoreText.setText("Score: " + score);
                                            lastPlatform = platform;
                                        }
                                        isOnPlatform = true;
                                        canJump = true;
                                        hitboxY = platform.y - hitboxHeight; // Align hitbox on top
                                        hitboxX -= platformSpeed;
                                        characterVelocity = 0;
                                    }
                                }

                                // PARTIAL SIDE COLLISION CHECK
                                int sideCollisionHeight = platform.height / 3;  // Only top 1/3 of the platform is solid on the sides
                                int buffer = (int) (hitboxWidth * 0.1); // Convert buffer to int to avoid type mismatch

                                boolean hitsLeftSide = hitboxX + hitboxWidth - buffer > platform.x  // Apply buffer
                                        && hitboxX < platform.x
                                        && hitboxY + hitboxHeight > platform.y
                                        && hitboxY < platform.y + sideCollisionHeight;  // Check only top portion

                                boolean hitsRightSide = hitboxX + buffer < platform.x + platform.width + buffer // Apply buffer
                                        && hitboxX + hitboxWidth > platform.x + platform.width
                                        && hitboxY + hitboxHeight > platform.y
                                        && hitboxY < platform.y + sideCollisionHeight;

                                if (hitsLeftSide) {
                                    hitboxX = platform.x - hitboxWidth + buffer; // Prevent sticking
                                    isOnPlatform = false;
                                    canJump = false;
                                }

                                if (platform.x < -platform.width) {
                                    platforms.remove(platform); // Remove safely
                                }

                                // Platform movement
                                long elapsedTime = System.currentTimeMillis() - startTime; // Get elapsed time in milliseconds
                                float timeFactor = Math.min(elapsedTime / 30000f, 1f); // Scale up over 30 seconds (adjust as needed)

                                platform.x -= 15f + (maxPlatformSpeed - 10f) * timeFactor;
                            }

                            // Remove Platforms that Move Off-Screen
                            platforms.removeIf(platform -> platform.x + platform.width < 0);

                            // If no platform was landed on, keep falling

                            if (!isOnPlatform) {
                                characterVelocity += gravity;
                                hitboxY += characterVelocity;
                            }

                            if (!canJump) {
                                isOnPlatform = false;
                            }

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

                            backgroundX -= platformSpeed + 5;

                            // Reset position for infinite scrolling
                            if (backgroundX <= -screenWidth) {
                                backgroundX = (int)((backgroundX - platformSpeed - 5) % screenWidth);
                            }

                            // Draw Background
                            canvas.drawBitmap(backgroundBitmap, backgroundX, 0, null);
                            canvas.drawBitmap(backgroundBitmap, backgroundX + screenWidth, 0, null);

                            // Draw Character
                            canvas.drawBitmap(currentFrame, hitboxX - 5, hitboxY - 35,null);

//                            float cornerRadius = 70f;
//                            // Draw Hitbox (DEBUGGING)
//                            paint.setColor(Color.BLUE); // Change to any color you like
//                            paint.setStyle(Paint.Style.STROKE); // Outline instead of fill
//                            paint.setStrokeWidth(5); // Thickness of the hitbox line
//                            canvas.drawRoundRect(
//                                    hitboxX, hitboxY,
//                                    hitboxX + hitboxWidth, hitboxY + hitboxHeight,
//                                    cornerRadius, cornerRadius, paint
//                            );


                            // Draw Platforms
                            paint.setColor(Color.RED);
                            for (Platform platform : platforms) {
                                platformBitmap = Bitmap.createScaledBitmap(platformBitmap, platform.width, platform.height, false);
                                if (platform.scaledBitmap == null) { // Only scale once
                                    platform.scaledBitmap = Bitmap.createScaledBitmap(platformBitmap, platform.width, platform.height, false);
                                }
                                canvas.drawBitmap(platform.scaledBitmap, platform.x, platform.y, null);
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

//        int platformHeight = 120 + random.nextInt(90);
        int platformWidth = random.nextInt(160) + 120;

        // Y Position Calculation (Ensure Platform is Above Character)
        int platformY;
        if (score >= 150) {
            int choice = random.nextInt(3);
            if (choice == 0) {
                platformY = standingPlatform.y - 80; // Front
            } else if (choice == 1) {
                platformY = standingPlatform.y - 130; // Mid
            } else {
                platformY = standingPlatform.y - 180; // Higher
            }
        } else if (score >= 50) {
            platformY = random.nextBoolean()
                    ? standingPlatform.y - 100
                    : standingPlatform.y - 200;
        } else {
            platformY = standingPlatform.y - (random.nextInt(200) + 90);
        }

        int platformHeight = screenHeight - platformY;

        // X Position Calculation (Ensure Proper Spacing)
        int lastPlatformX = platforms.isEmpty() ? 700 : platforms.get(platforms.size() - 1).x;
        int lastPlatformWidth = platforms.isEmpty() ? 0 : platforms.get(platforms.size() - 1).width;

        // Speed-based Platform Generation
        float timeElapsed = (System.currentTimeMillis() - startTime) / 1000f; // Time in seconds
        float platformSpeed = 15f + (maxPlatformSpeed - 15f) * (timeElapsed / 30f); // Linear speed increase
        platformSpeed = Math.min(platformSpeed, maxPlatformSpeed); // Cap at max speed

        // Adjust platform spacing
        int minSpacing = 380;
        int maxSpacing = 1000;
        float speedFactor = (platformSpeed - 15f) / (maxPlatformSpeed - 15f);
        int spacing = minSpacing + (int) ((maxSpacing - minSpacing) * speedFactor);

        int platformX = lastPlatformX + lastPlatformWidth + spacing;

        // Add new platform
        platforms.add(new Platform(platformX, platformY, platformWidth, platformHeight));
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

        // Reset the start time (IMPORTANT)
        startTime = System.currentTimeMillis();

        // Re-add the starting platform
        int initialPlatformWidth = 400;
        int initialPlatformHeight = screenHeight - 1;
        int initialPlatformX = 80;
        int initialPlatformY = characterY + 50;
        platforms.add(new Platform(initialPlatformX, initialPlatformY, initialPlatformWidth, initialPlatformHeight));

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
        gravity = 2.4f;
        isOnPlatform = true;

        // Reset character & hitbox positions
        characterX = 150;
        characterY = 1510;
        hitboxX = characterX + 25;
        hitboxY = characterY - 135;

        // Reset the start time to 0
        startTime = System.currentTimeMillis();

        // Clear platforms and re-add the starting platform
        platforms.clear();
        int initialPlatformWidth = 400;
        int initialPlatformHeight = screenHeight - 1;
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
        }, 120); // Small delay to ensure reset completes properly

        gameOverMenu.setVisibility(Button.GONE);
    }

    // Pauses the game
    private void pauseGame() {
        isPlaying = false;  // Stop game loop
        pauseMenu.setVisibility(RelativeLayout.VISIBLE);
    }

    // Resumes the game
    private void resumeGame() {
        isPlaying = true;
        pauseMenu.setVisibility(Button.GONE);
        gameHandler.post(gameLoop);  // Resume game loop
    }

    private void gameOver() {
        isPlaying = false;  // Stop the game
        finalScoreText.setText("Final Score: " + score);
        gameOverMenu.setVisibility(RelativeLayout.VISIBLE);  // Show the restart button
        btnRestart.setVisibility(Button.VISIBLE);
        gameHandler.removeCallbacks(gameLoop);
        speechRecognizer.stopListening();  // Stop listening for voice commands
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