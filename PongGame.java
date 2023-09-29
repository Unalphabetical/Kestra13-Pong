package com.gamecodeschool.c11pong;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

@SuppressLint("ViewConstructor")
class PongGame extends SurfaceView implements Runnable {

    private MotionEvent motionEvent;

    private boolean mGameOver;

    // These objects are needed to do the drawing
    private final SurfaceHolder mOurHolder;
    private Canvas mCanvas;
    private final Paint mPaint;

    // How many frames per second did we get?
    private long mFPS;

    // Holds the resolution of the screen
    private final int mScreenX;
    private final int mScreenY;
    // How big will the text be?
    private final int mFontSize;
    private final int mFontMargin;

    private final int mNameTop;

    private final int mNameLeft;

    // The game objects
    private final Bat mBat;
    private final Ball mBall;

    // The current score and lives remaining
    private int mScore = 0;
    private int mLives = 3;

    // Here is the Thread and two control variables
    private Thread mGameThread = null;
    // This volatile variable can be accessed
    // from inside and outside the thread
    private volatile boolean mPlaying;
    private boolean mPaused = true;

    // All these are for playing sounds
    private final SoundPool mSP;
    private int mBeepID = -1;
    private int mBoopID = -1;
    private int mBopID = -1;
    private int mMissID = -1;

    // The PongGame constructor
    // Called when this line:
    // mPongGame = new PongGame(this, size.x, size.y);
    // is executed from PongActivity
    public PongGame(Context context, int x, int y) {
        // Super... calls the parent class
        // constructor of SurfaceView
        // provided by Android
        super(context);

        // Initialize these two members/fields
        // With the values passed in as parameters
        mScreenX = x;
        mScreenY = y;

        // Font is 5% (1/20th) of screen width
        mFontSize = mScreenX / 20;
        // Margin is 1.5% (1/75th) of screen width
        mFontMargin = mScreenX / 75;

        mNameTop = mScreenX / 20;

        mNameLeft = mScreenX - 660;

        // Initialize the objects
        // ready for drawing with
        // getHolder is a method of SurfaceView
        mOurHolder = getHolder();
        mPaint = new Paint();

        // Initialize the bat and ball
        mBall = new Ball(mScreenX);
        mBat = new Bat(mScreenX, mScreenY);

        // Prepare the SoundPool instance
        // Depending upon the version of Android

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mSP = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else {
            mSP = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        }



        // Open each of the sound files in turn
        // and load them in to Ram ready to play
        // The try-catch blocks handle when this fails
        // and is required.
        try{
            AssetManager assetManager = context.getAssets();
            AssetFileDescriptor descriptor;

            descriptor = assetManager.openFd("beep.ogg");
            mBeepID = mSP.load(descriptor, 0);

            descriptor = assetManager.openFd("boop.ogg");
            mBoopID = mSP.load(descriptor, 0);

            descriptor = assetManager.openFd("bop.ogg");
            mBopID = mSP.load(descriptor, 0);

            descriptor = assetManager.openFd("miss.ogg");
            mMissID = mSP.load(descriptor, 0);


        }catch(IOException e){
            Log.e("error", "failed to load sound files");
        }

        // Everything is ready so start the game
        startNewGame();
    }

    // The player has just lost
    // or is starting their first game
    private void startNewGame() {

        // Put the ball back to the starting position
        mBall.reset(mScreenX, mScreenY);

        // Rest the score and the player's chances
        mScore = 0;
        mLives = 3;

        mGameOver = false;
    }

    // When we start the thread with:
    // mGameThread.start();
    // the run method is continuously called by Android
    // because we implemented the Runnable interface
    // Calling mGameThread.join();
    // will stop the thread
    @Override
    public void run() {
        // mPlaying gives us finer control
        // rather than just relying on the calls to run
        // mPlaying must be true AND
        // the thread running for the main loop to execute
        while (mPlaying) {

            // What time is it now at the start of the loop?
            long frameStartTime = System.currentTimeMillis();

            // Provided the game isn't paused call the update method
            if(!mPaused){
                update();
                // Now the bat and ball are in their new positions
                // we can see if there have been any collisions
                detectCollisions();

            }

            // The movement has been handled and collisions
            // detected now we can draw the scene.
            draw();

            // How long did this frame/loop take?
            // Store the answer in timeThisFrame
            long timeThisFrame = System.currentTimeMillis() - frameStartTime;

            // Make sure timeThisFrame is at least 1 millisecond
            // because accidentally dividing by zero crashes the game
            if (timeThisFrame > 0) {
                // Store the current frame rate in mFPS
                // ready to pass to the update methods of
                // mBat and mBall next frame/loop
                // The number of milliseconds in a second
                int MILLIS_IN_SECOND = 1000;
                mFPS = MILLIS_IN_SECOND / timeThisFrame;
            }

        }

    }

    private void update() {
        // Update the bat and the ball
        mBall.update(mFPS);
        mBat.update(mFPS);
    }

    private void detectCollisions(){
        // Has the bat hit the ball?
        if(RectF.intersects(mBat.getRect(), mBall.getRect())) {
            // Realistic-ish bounce
            mBall.batBounce(mBat.getRect());
            mBall.increaseVelocity();
            mScore++;
            mSP.play(mBeepID, 1, 1, 0, 0, 1);
            // For every 5 points, you gain a life!
            if (mScore % 5 == 0) {
                mLives++;
            }
        }

        // Has the ball hit the edge of the screen

        // Bottom
        if (mBall.getRect().bottom > mScreenY) {
            mBall.reverseYVelocity();
            mLives--;

            if (mLives <= 0) {
                // Player has run out of lives, set game over
                mGameOver = true;
                mPaused = true;
            } else {
                // Player still has lives, continue the game
                mBall.reset(mScreenX, mScreenY);
            }

            mSP.play(mMissID, 1, 1, 0, 0, 1);
        }

        // Top
        if(mBall.getRect().top < 0){
            mBall.reverseYVelocity();
            mSP.play(mBoopID, 1, 1, 0, 0, 1);
        }

        // Left
        if(mBall.getRect().left < 0){
            mBall.reverseXVelocity();
            mSP.play(mBopID, 1, 1, 0, 0, 1);
        }

        // Right
        if(mBall.getRect().right > mScreenX){
            mBall.reverseXVelocity();
            mSP.play(mBopID, 1, 1, 0, 0, 1);
        }

    }

    // Draw the game objects and the HUD
    void draw() {
        if (mOurHolder.getSurface().isValid()) {
            // Lock the canvas (graphics memory) ready to draw
            mCanvas = mOurHolder.lockCanvas();

            // Set the background color based on game state
            // Change this to your desired color
            int mGameOverBackgroundColor = Color.RED;
            if (mGameOver) {
                // Game over background color
                mCanvas.drawColor(mGameOverBackgroundColor);
            } else {
                // Regular gameplay background color
                mCanvas.drawColor(Color.argb(255, 26, 128, 182));
            }

            // Choose a color to paint with
            mPaint.setColor(Color.argb(255, 255, 255, 255));

            // Fill the screen with a solid color
            mCanvas.drawColor(Color.argb(255, 26, 128, 182));

            // Choose a color to paint with
            mPaint.setColor(Color.argb(255, 255, 255, 255));

            // Draw the bat and ball
            mCanvas.drawRect(mBall.getRect(), mPaint);
            mCanvas.drawRect(mBat.getRect(), mPaint);

            // Choose the font size
            mPaint.setTextSize(mFontSize);

            if (mGameOver) {
                // Game over background color
                mCanvas.drawColor(mGameOverBackgroundColor);

                // Display the game over message
                mCanvas.drawText("Game Over", mScreenX / 2 - 100, mScreenY / 2, mPaint);

                mCanvas.drawText("Tap anywhere to Start.", mScreenX / 2 - 300, mScreenY / 2 + 100, mPaint);
            } else {
                // Draw the HUD
                mCanvas.drawText("Score: " + mScore +
                                "   Lives: " + mLives,
                        mFontMargin , mFontSize, mPaint);

                mCanvas.drawText("Kestine Tran", mNameLeft, mNameTop, mPaint);

                // Are we debugging?
                boolean DEBUGGING = true;
                if(DEBUGGING){
                    printDebuggingText();
                }
            }

            // Check for taps on the game over screen to reset the game
            if (mGameOver && isGameOverTapped(motionEvent.getX(), motionEvent.getY())) {
                startNewGame();
                mGameOver = false;
            }

            // Display the drawing on screen
            // unlockCanvasAndPost is a method of SurfaceView
            mOurHolder.unlockCanvasAndPost(mCanvas);
        }

    }

    // Handle all the screen touches
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        this.motionEvent = motionEvent;

        // This switch block replaces the
        // if statement from the Sub Hunter game
        switch (motionEvent.getAction() &
                MotionEvent.ACTION_MASK) {

            // The player has put their finger on the screen
            case MotionEvent.ACTION_DOWN:

                // If the game was paused unpause
                mPaused = false;

                // Where did the touch happen
                if(motionEvent.getX() > mScreenX / 2){
                    // On the right hand side
                    mBat.setMovementState(mBat.RIGHT);
                }
                else{
                    // On the left hand side
                    mBat.setMovementState(mBat.LEFT);
                }

                break;

            // The player lifted their finger
            // from anywhere on screen.
            // It is possible to create bugs by using
            // multiple fingers. We will use more
            // complicated and robust touch handling
            // in later projects
            case MotionEvent.ACTION_UP:

                // Stop the bat moving
                mBat.setMovementState(mBat.STOPPED);
                break;
        }
        return true;
    }

    private void printDebuggingText(){
        int debugSize = mFontSize / 2;
        int debugStart = 150;
        mPaint.setTextSize(debugSize);
        mCanvas.drawText("FPS: " + mFPS ,
                10, debugStart + debugSize, mPaint);
    }

    // This method is called by PongActivity
    // when the player quits the game
    public void pause() {

        // Set mPlaying to false
        // Stopping the thread isn't
        // always instant
        mPlaying = false;
        try {
            // Stop the thread
            mGameThread.join();
        } catch (InterruptedException e) {
            Log.e("Error:", "joining thread");
        }

    }


    // This method is called by PongActivity
    // when the player starts the game
    public void resume() {
        mPlaying = true;
        // Initialize the instance of Thread
        mGameThread = new Thread(this);

        // Start the thread
        mGameThread.start();
    }

    private boolean isGameOverTapped(float x, float y) {
        // Define the coordinates of the game over message region
        float gameOverMessageLeft = mScreenX / 2 - 100;
        float gameOverMessageRight = mScreenX / 2 + 100;
        float gameOverMessageTop = mScreenY / 2 - mFontSize;
        float gameOverMessageBottom = mScreenY / 2 + mFontSize;

        // Check if the tap coordinates (x, y) are within the region
        if (x >= gameOverMessageLeft && x <= gameOverMessageRight &&
                y >= gameOverMessageTop && y <= gameOverMessageBottom) {
            return true;
        }

        return false;
    }
}