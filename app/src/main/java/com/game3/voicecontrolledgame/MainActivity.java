package com.game3.voicecontrolledgame;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
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
import android.view.View;
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
    private boolean isPlaying = false;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private Handler gameHandler = new Handler();
    private Runnable gameLoop;
    private List<Platform> platforms = new ArrayList<>();
    private List<Flower> flowers = new ArrayList<>();
    private Platform standingPlatform;
    private Handler scoreHandler = new Handler(Looper.getMainLooper());
    private Runnable scoreRunnable;
    private float platformSpeed = 0f; // Initial platform speed
    private final float maxPlatformSpeed = 50f; // Maximum platform speed
    private final int maxScore = 1200; // Score at which max speed is reached
    private boolean isOnPlatform;
    private float characterVelocity = 0f; // Vertical velocity
    private float gravity = 2.3f;  // Gravity acceleration
    private final float jumpMultiplier = 23f; // Adjust jump strength

    private final float maxGravity = 2f ;  // Maximum gravity cap
    private int frameIndex = 0;
    private long lastFrameTime = 0;
    private int frameDelay = 100;
    private Platform lastPlatform = null;
    private boolean canJump = true;
    private Bitmap[] characterRunFrames, characterJumpFrames, flowerBitmaps, characterFrames;
    private Bitmap characterBitmap, backgroundBitmap, platformBitmap;
    private Set<Platform> usedPlatforms = new HashSet<>(); // Move this outside the method
    private int flowerX, flowerY;


    // Hitbox
    private int characterY = 1510;
    private int characterX = 150;
    int characterWidth = 180; // Set your desired width
    int characterHeight = 225; // Set your desired height
    int hitboxWidth = characterWidth - 60;  // Adjust width
    int hitboxHeight = characterHeight - 60; // Adjust height
    int hitboxX = characterX + 45; // Center hitbox inside sprite
    int hitboxY = characterY - 135; // Adjust vertical alignment
    int desiredCharacterWidth, desiredCharacterHeight;
    private int backgroundX = 0;
    long startTime = System.currentTimeMillis();

    // Menus
    Button btnResume, btnRestart, startButton;
    ImageButton btnPause;
    RelativeLayout pauseMenu, gameOverMenu;
    private TextView scoreText, finalScoreText;
    boolean isPaused = false;
    private MediaPlayer bgMusic, gameMusic;



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
    private static class Flower {
        public float velocity;
        public Bitmap scaledBitmap;
        int x, y, width, height;

        public Flower(int x, int y, int width, Bitmap[] bitmaps) {
            this.x = x;
            this.y = y;
            this.width = width;

            // Assign a random bitmap from the array
            int randomIndex = new Random().nextInt(bitmaps.length);
            Bitmap originalBitmap = bitmaps[randomIndex];

            // Scale the selected bitmap
            this.scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width * 4, width * 4, false);
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

        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnRestart = findViewById(R.id.btnRestart);
        pauseMenu = findViewById(R.id.pauseMenu);
        gameOverMenu = findViewById(R.id.gameOverMenu);
        startButton = findViewById(R.id.btnStartGame);

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

        startButton.setOnClickListener(v -> {
            isPlaying = true;
            startButton.setVisibility(Button.GONE); // Hide the button after starting
            startGame(); // Start the game loop
        });

        Bitmap spriteSheet = BitmapFactory.decodeResource(getResources(), R.drawable.character_spritesheet);
        int totalFrames = 4;
        int frameWidth = spriteSheet.getWidth() / totalFrames; // Each frame is equally spaced
        int frameHeight = spriteSheet.getHeight();

        // Desired character size
        desiredCharacterWidth = 340;  // Adjust as needed
        desiredCharacterHeight = 390; // Adjust as needed

        characterFrames = new Bitmap[totalFrames];

        for (int i = 0; i < totalFrames; i++) {
            Bitmap frame = Bitmap.createBitmap(spriteSheet, i * frameWidth, 0, frameWidth, frameHeight);
            characterFrames[i] = Bitmap.createScaledBitmap(frame, desiredCharacterWidth, desiredCharacterHeight, true);
        }

        // Assign scaled frames to animations
        characterRunFrames = new Bitmap[]{
                characterFrames[0], characterFrames[1], characterFrames[2], characterFrames[3]
        };

        characterJumpFrames = new Bitmap[]{
                characterFrames[1], characterFrames[3]
        };


        flowerBitmaps = new Bitmap[]{
                BitmapFactory.decodeResource(getResources(), R.drawable.cosmos),
                BitmapFactory.decodeResource(getResources(), R.drawable.flower11),
                BitmapFactory.decodeResource(getResources(), R.drawable.orchid),
                BitmapFactory.decodeResource(getResources(), R.drawable.marrigold),
                BitmapFactory.decodeResource(getResources(), R.drawable.daffodil),
                BitmapFactory.decodeResource(getResources(), R.drawable.jasmin)
        };

        platformBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pform);

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
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isPlaying) return true; // Ignore touch input if game hasn't started

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isOnPlatform && canJump) {
                isOnPlatform = false;
                canJump = false;
                characterVelocity = -50;
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
        // startBGM();
        scoreText.setVisibility(View.VISIBLE);
        btnPause.setVisibility(Button.VISIBLE);

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
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // Clear the canvas

                            // Adjust platform speed dynamically based on voice strength
                            float platformSpeed = (voiceStrength >= 5) ? Math.min(voiceStrength * 3.0f, 55) : 0;

                            // Move platforms left
                            for (Platform platform : platforms) {
                                platform.x -= platformSpeed; // Move dynamically
                            }

                            // Handle Jumping (Prevent Double Jump)
                            if (voiceStrength > 9.99 && isOnPlatform) {
                                isOnPlatform = false;
                                characterVelocity = -Math.min(voiceStrength * 5.5f, 65);
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
                            Iterator<Platform> platformIterator = platforms.iterator();
                            while (platformIterator.hasNext()) {
                                Platform platform = platformIterator.next();

                                int hitboxBottom = hitboxY + hitboxHeight;
                                boolean isAbovePlatform = hitboxY < platform.y;
                                boolean isWithinPlatformWidth = hitboxX + hitboxWidth * 0.8f >= platform.x
                                        && hitboxX + hitboxWidth * 0.2f <= platform.x + platform.width;

                                // Remove Platforms That Move Off-Screen**
                                if (platform.x + platform.width < 0) {
                                    platformIterator.remove();
                                    continue;
                                }

                                // LANDING CHECK
                                if (platform.x + platform.width > 0) {
                                    if (hitboxBottom >= platform.y - 5 && isAbovePlatform && isWithinPlatformWidth) {
                                        if (!isOnPlatform) { // Score only when transitioning from air to platform
                                            score += 10;
                                            scoreText.setText("Score: " + score);
                                            lastPlatform = platform;
                                        }
                                        isOnPlatform = true;
                                        canJump = true;
                                        hitboxY = platform.y - hitboxHeight; // Align hitbox on top
                                        // hitboxX -= platformSpeed;
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

                                // Platform movement
//                                long elapsedTime = System.currentTimeMillis() - startTime; // Get elapsed time in milliseconds
//                                float timeFactor = Math.min(elapsedTime / 30000f, 1f); // Scale up over 30 seconds (adjust as needed)
//                                platform.x -= 15f + (40f - 10f) * timeFactor;

                            }

                            // If no platform was landed on, keep falling
                            if (!isOnPlatform) {
                                characterVelocity += gravity;
                                hitboxY += characterVelocity;
                            }

                            if (!canJump) {
                                isOnPlatform = false;
                            }

                            // Generate New Platforms
                            if (platforms.isEmpty() || platforms.get(platforms.size() - 1).x < screenWidth - 350) {
                                generateRandomPlatform();
                            }

                            if (Math.random() < 0.01 ) { // 1% chance per frame to spawn a flower
                                generateRandomFlower();
                            }

                            // Move & Remove Flowers
                            for (Iterator<Flower> iterator = flowers.iterator(); iterator.hasNext(); ) {
                                Flower flower = iterator.next();

                                // Apply gravity (like character)
                                flower.y += flower.velocity;
                                flower.velocity += gravity * 0.9f;

                                long elapsedTime = System.currentTimeMillis() - startTime; // Get elapsed time in milliseconds
                                float timeFactor = Math.min(elapsedTime / 30000f, 1f); // Scale up over 30 seconds (adjust as needed)

                                flower.x -= platformSpeed;


                                // **Check for Landing on Platforms**
                                for (Platform platform : platforms) {
                                    boolean isAbovePlatform = flower.y + flower.height <= platform.y;
                                    boolean isWithinPlatformWidth = flower.x + flower.width >= platform.x && flower.x <= platform.x + platform.width;

                                    if (isAbovePlatform && isWithinPlatformWidth && flower.y + flower.height + flower.velocity >= platform.y) {
                                        flower.y = platform.y - flower.height - 30; // Ensure it lands exactly on top
                                        flower.velocity = 0;
                                    }
                                }

                                // Remove Off-Screen Flowers
                                if (flower.y > screenHeight || flower.x + flower.width < 0) {
                                    iterator.remove();
                                }

                                // **Check if Character Collects It**
                                boolean isTouchingCharacter = hitboxX < flower.x + flower.width && hitboxX + hitboxWidth > flower.x &&
                                        hitboxY < flower.y + flower.height && hitboxY + hitboxHeight > flower.y;
                                if (isTouchingCharacter) {
                                    score += 5;
                                    scoreText.setText("Score: " + score);
                                    iterator.remove();
                                }
                            }

                            // Select Character Sprite Based on State
                            Bitmap currentFrame;
                            if (!isOnPlatform) {
                                currentFrame = (characterVelocity < 0) ? characterJumpFrames[1] : characterJumpFrames[0]; // Jump or land
                            } else {
                                if (platformSpeed > 0 && System.currentTimeMillis() - lastFrameTime > frameDelay) {
                                    frameIndex = (frameIndex + 1) % characterRunFrames.length; // Loop through run frames only when moving
                                    lastFrameTime = System.currentTimeMillis();
                                }
                                currentFrame = characterRunFrames[frameIndex]; // Running animation
                            }

                            backgroundX -= platformSpeed + 4;

                            // Reset position for infinite scrolling
                            if (backgroundX <= -screenWidth) {
                                backgroundX += screenWidth;
                            }

                            // Draw Background
                            canvas.drawBitmap(backgroundBitmap, backgroundX, 0, null);
                            canvas.drawBitmap(backgroundBitmap, backgroundX + screenWidth, 0, null);

                            // Draw Platforms
                            paint.setColor(Color.RED);
                            for (Platform platform : platforms) {
                                platformBitmap = Bitmap.createScaledBitmap(platformBitmap, platform.width, platform.height, false);
                                if (platform.scaledBitmap == null) { // Only scale once
                                    platform.scaledBitmap = Bitmap.createScaledBitmap(platformBitmap, platform.width, platform.height, false);
                                }
                                canvas.drawBitmap(platform.scaledBitmap, platform.x, platform.y, null);
                            }

                            // Draw Character
                            float spriteX = hitboxX - 95; // Center horizontally
                            float spriteY = hitboxY - 50; // Align feet with the hitbox

                            canvas.drawBitmap(currentFrame, spriteX, spriteY, null);

                            // Draw Flowers
                            for (Flower flower : flowers) {
                                int radius = flower.width * 2 ;

                                // Draw the assigned bitmap
                                if (flower.scaledBitmap != null) {
                                    canvas.drawBitmap(flower.scaledBitmap, flower.x - radius, flower.y - radius, null);
                                }
//                                Paint hitboxPaint = new Paint();
//                                hitboxPaint.setColor(Color.RED);
//                                hitboxPaint.setStyle(Paint.Style.STROKE);
//                                hitboxPaint.setStrokeWidth(5);
//
//                                int hitboxRadius = flower.width ; // Adjust the size to match expected hitbox
//                                canvas.drawCircle(flower.x, flower.y, hitboxRadius, hitboxPaint);
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

//      int platformHeight = 120 + random.nextInt(90);
        int platformWidth = random.nextInt(140) + 130;

        // Y Position Calculation (Ensure Platform is Above Character)
        int platformY;
        if (score >= 200) {
            int choice = random.nextInt(3);
            if (choice == 0) {
                platformY = standingPlatform.y - 40; // Front
            } else if (choice == 1) {
                platformY = standingPlatform.y - 90; // Mid
            } else {
                platformY = standingPlatform.y - 140; // Higher
            }
        } else if (score >= 100) {
            platformY = random.nextBoolean()
                    ? standingPlatform.y - 60
                    : standingPlatform.y - 160;
        } else {
            platformY = standingPlatform.y - (random.nextInt(160) + 50);
        }

        int platformHeight = screenHeight - platformY;

        // X Position Calculation (Ensure Proper Spacing)
        int lastPlatformX = platforms.isEmpty() ? 700 : platforms.get(platforms.size() - 1).x;
        int lastPlatformWidth = platforms.isEmpty() ? 0 : platforms.get(platforms.size() - 1).width;

        // Speed-based Platform Generation
        float timeElapsed = (System.currentTimeMillis() - startTime) / 1000f; // Time in seconds
        float platformSpeed = 15f + (maxPlatformSpeed - 15f) * (timeElapsed / 40f); // Linear speed increase
        platformSpeed = Math.min(platformSpeed, maxPlatformSpeed); // Cap at max speed

        // Adjust platform spacing
        int minSpacing = 380;
        int maxSpacing = 930;
        float speedFactor = (platformSpeed - 15f) / (maxPlatformSpeed - 15f);
        int spacing = minSpacing + (int) ((maxSpacing - minSpacing) * speedFactor);

        int platformX = lastPlatformX + lastPlatformWidth + spacing;

        // Add new platform
        platforms.add(new Platform(platformX, platformY, platformWidth, platformHeight));
    }

    private void generateRandomFlower() {
        Random random = new Random();
        boolean spawnOnPlatform = random.nextBoolean(); // 50% chance to spawn on a platform

        int flowerWidth = 35; // Example size

        if (spawnOnPlatform && !platforms.isEmpty()) {
            // Find a platform that hasn't been used
            List<Platform> availablePlatforms = new ArrayList<>();
            for (Platform platform : platforms) {
                if (!usedPlatforms.contains(platform)) {
                    availablePlatforms.add(platform);
                }
            }

            if (!availablePlatforms.isEmpty()) {
                Platform randomPlatform = availablePlatforms.get(random.nextInt(availablePlatforms.size()));
                usedPlatforms.add(randomPlatform); // Mark platform as used

                // Spawn on a random platform
                flowerX = randomPlatform.x + random.nextInt(randomPlatform.width - flowerWidth);
                flowerY = randomPlatform.y - flowerWidth;
            } else {
                spawnOnPlatform = false;
            }
        }

        if (!spawnOnPlatform) {
            flowerX = random.nextInt(screenWidth + 100);
            flowerY = -random.nextInt(300) - 50;
        }

        flowers.add(new Flower(flowerX, flowerY, flowerWidth, flowerBitmaps));
    }


    private void resetGame() {
        // Clear all previous handlers to prevent conflicts
        gameHandler.removeCallbacks(gameLoop);
        // stopBGM();

        // Clear all platforms\
        usedPlatforms.clear();
        platforms.clear();
        flowers.clear();

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
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // Clear the canvas
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void restartGame() {
        // Stop the current game loop
        isPlaying = false;
        gameHandler.removeCallbacks(gameLoop);
        // startBGM();

        // Reset game variables
        score = 0;
        scoreText.setText("Score: " + score);
        characterVelocity = 0;
        gravity = 2.3f;
        isOnPlatform = true;

        flowers.clear();
        usedPlatforms.clear();

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
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
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
        // bgMusic.pause();
    }

    // Resumes the game
    private void resumeGame() {
        isPlaying = true;
        pauseMenu.setVisibility(Button.GONE);
        gameHandler.post(gameLoop);  // Resume game loop
        // bgMusic.start();
    }

    private void gameOver() {
        gameOverMenu.setVisibility(View.VISIBLE);
        btnRestart.setVisibility(Button.VISIBLE);
        gameOverMenu.bringToFront();
        finalScoreText.setText("Final Score: " + score);
        // stopBGM();

        gameHandler.removeCallbacks(gameLoop);
        speechRecognizer.stopListening();  // Stop listening for voice commands
        isPlaying = false;  // Stop the game
    }

    private void startBGM() {
        if (bgMusic == null) {
            bgMusic = MediaPlayer.create(this, R.raw.bgmusic);
            bgMusic.setLooping(true); // Loop the music
            bgMusic.setVolume(2f, 2f); // Adjust volume (0.0 - 1.0)
        }
        bgMusic.start();
    }

    private void stopBGM() {
        if (bgMusic != null) {
            bgMusic.stop();
            bgMusic.release();
            bgMusic = null;
        }
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