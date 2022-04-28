import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private static int maxFrames;

    private PlayerWindow window;

    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean PlayerPausado = true;
    private Song currentSong;
    private int FrameAtual = 0;
    private int newFrame;

    private Thread threadPlayMusic;
    private ArrayList<Song> ArrayOfSongs = new ArrayList<>();
    private ReentrantLock lock = new ReentrantLock();
    private ReentrantLock lockPlayPause = new ReentrantLock();
    private Condition verificadorPlayPause = lockPlayPause.newCondition();

    private int IndiceMusicaAtual;
    int IndiceMusicaSelecionada = -1;
    private boolean NextAcionado = false;
    private boolean PreviousAcionado = false;
    private int NovoFrame;
    private boolean StopAcionado = false;
    private boolean DontSkip = false;
    private boolean IsPlaying = false;

    public Player() {

        //button events
        ActionListener buttonListenerPlayNow = e -> {
            start(window.getSelectedSong());

        };
        ActionListener buttonListenerRemove = e -> removeFromQueue(window.getSelectedSong());
        ActionListener buttonListenerAddSong = e -> {
            addToQueue();
        };
        ActionListener buttonListenerPlayPause = e -> {
            if (PlayerPausado){
                resume();
            } else{
                pause();
            }
        };
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerShuffle = e -> {
            shuffle = !shuffle;
            if (shuffle) {
                pause();
            }
        };
        ActionListener buttonListenerRepeat = e -> {
            repeat = !repeat;
            if (repeat) {
                pause();
            }
        };

        //mouse events
        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                NovoFrame = (window.getScrubberValue()/(int)currentSong.getMsPerFrame());
                window.setTime(FrameAtual *(int)currentSong.getMsPerFrame(), (int)currentSong.getMsLength());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        };
        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pause();
                NovoFrame = (window.getScrubberValue()/(int)currentSong.getMsPerFrame());
                window.setTime(FrameAtual *(int)currentSong.getMsPerFrame(), (int)currentSong.getMsLength());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                try{
                    if (NovoFrame >= FrameAtual){
                        skipToFrame(NovoFrame);
                    } else {
                        bitstream.close();
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        device.open(decoder = new Decoder());
                        bitstream = new Bitstream(currentSong.getBufferedInputStream());
                        FrameAtual = 0;
                        skipToFrame(NovoFrame);
                    }

                    resume();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                } catch (BitstreamException ex) {
                    ex.printStackTrace();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        };

        String windowTitle = "PlayerPessoal";

        window = new PlayerWindow(
                windowTitle,
                getDisplayInfo(),
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerRepeat,
                scrubberListenerClick,
                scrubberListenerMotion);
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        FrameAtual++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > FrameAtual) {
            int framesToSkip = newFrame - FrameAtual;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }

    //</editor-fold>
    private String[][] getDisplayInfo() {
        String[][] ArrayAuxiliar = new String[ArrayOfSongs.size()][];

        for (int i = 0; i < ArrayOfSongs.size(); i++) {
            ArrayAuxiliar[i] = ArrayOfSongs.get(i).getDisplayInfo();
        }
        return ArrayAuxiliar;
    }

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    Song newSong = window.getNewSong();

                    boolean MusicaNaPlaylist = false;
                    for (int i = 0; i < ArrayOfSongs.size(); i++) {
                        if (newSong.getFilePath().equals(ArrayOfSongs.get(i).getFilePath())) {
                            MusicaNaPlaylist = true;
                            break;
                        }
                    }
                    if (!MusicaNaPlaylist) ArrayOfSongs.add(newSong);

                    window.updateQueueList(getDisplayInfo());

                } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException dq) {
                    System.out.println("Não funfou");
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    public void removeFromQueue(String filePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();

                    IndiceMusicaSelecionada = window.getSelectedSongIndex();

                    if (IndiceMusicaAtual == IndiceMusicaSelecionada){
                        IndiceMusicaAtual -= 1;
                        next();
                        if (IndiceMusicaAtual == ArrayOfSongs.size() - 1){
                            stop();
                        }
                    }
                    else if (IndiceMusicaSelecionada < IndiceMusicaAtual){
                        IndiceMusicaAtual -= 1;
                        if (IndiceMusicaAtual == 0){
                            window.setEnabledPreviousButton(false);
                        }
                    }
                    else{
                        if (IndiceMusicaAtual == ArrayOfSongs.size() -2){
                            window.setEnabledNextButton(false);
                        }
                    }
                    ArrayOfSongs.remove(IndiceMusicaSelecionada);
                    window.updateQueueList(getDisplayInfo());
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    public void getQueueAsArray() {
    }

    class RunnablePlay implements Runnable {
        @Override
        public void run() {
            try {
                window.setEnabledStopButton(true);
                window.setEnabledScrubber(true);
                while (IndiceMusicaAtual < ArrayOfSongs.size()){
                    IsPlaying = true;
                    if (IndiceMusicaAtual == ArrayOfSongs.size() - 1){
                        window.setEnabledNextButton(false);
                    } else{
                        window.setEnabledNextButton(true);
                    }
                    if (IndiceMusicaAtual == 0){
                        window.setEnabledPreviousButton(false);
                    } else{
                        window.setEnabledPreviousButton(true);
                    }
                    currentSong = ArrayOfSongs.get(IndiceMusicaAtual);
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());
                    PlayerPausado = false;
                    FrameAtual = 0;

                    while (playNextFrame()) {
                        lockPlayPause.lock();
                        try{
                            if (StopAcionado){
                                StopAcionado = false;
                                FrameAtual = 0;
                                window.resetMiniPlayer();
                                DontSkip = true;
                                break;
                            }
                            if (PlayerPausado){
                                verificadorPlayPause.await();
                            }
                            if (NextAcionado){
                                NextAcionado = false;
                                break;
                            }
                            if (PreviousAcionado){
                                PreviousAcionado = false;
                                IndiceMusicaAtual -=2;
                                break;
                            }
                            window.setTime(FrameAtual * (int) currentSong.getMsPerFrame(), (int) currentSong.getMsLength());
                            FrameAtual++;

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            lockPlayPause.unlock();
                        }
                    }
                    if (DontSkip == false){
                        FrameAtual = 0;
                        IndiceMusicaAtual++;
                    }
                    else{
                        DontSkip = false;
                        break;
                    }
                }
                IsPlaying = false;
                window.resetMiniPlayer();
            } catch (JavaLayerException | FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void start(String musicStart) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    if (IsPlaying == false){
                        for (int i = 0; i < ArrayOfSongs.size(); i++) {
                            if (musicStart.equals(ArrayOfSongs.get(i).getFilePath())) {
                                IndiceMusicaAtual = i;
                                threadPlayMusic = new Thread(new RunnablePlay());
                                PlayerPausado = false;
                                window.updatePlayPauseButtonIcon(PlayerPausado);
                                window.setEnabledPlayPauseButton(!PlayerPausado);
                                threadPlayMusic.start();
                            }
                        }
                    }
                    else {
                        IndiceMusicaSelecionada = window.getSelectedSongIndex();
                        IndiceMusicaAtual = IndiceMusicaSelecionada - 1;
                        next();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }


    public void stop() {
        StopAcionado = true;
    }

    public void pause() {
        PlayerPausado = true;
        window.updatePlayPauseButtonIcon(PlayerPausado);
    }

    public void resume() {
        PlayerPausado = false;
        lockPlayPause.lock();
        try{
            verificadorPlayPause.signalAll();
        } finally {
            lockPlayPause.unlock();
        }
        window.updatePlayPauseButtonIcon(PlayerPausado);
    }

    public void next() {
        NextAcionado = true;
    }

    public void previous() {
        PreviousAcionado = true;
    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>
}