import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.CustomFileChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;

public class Demo {

    private static Thread playerThread;
    private static Bitstream bitstream;
    private static Decoder decoder;
    private static AudioDevice device;
    private static DemoWindow demoWindow;
    private static int maxFrames;
    private int currentFrame;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            demoWindow = new DemoWindow();
        });
    }

    static final class DemoWindow extends JFrame{
        private final JProgressBar progressBar = new JProgressBar();

        public DemoWindow() {
            //<editor-fold desc="Componentes Básicos">
            this.setTitle("Demo");
            this.setLayout(new BorderLayout());
            this.setSize(300,100);
            this.setResizable(false);
            this.setLocationRelativeTo(null);
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            buttonPanel.add(Box.createHorizontalGlue());
            JButton chooseFileButton = new JButton("Choose mp3 file to play...");
            buttonPanel.add(chooseFileButton);
            //</editor-fold>

            ActionListener buttonListener = e -> {
                CustomFileChooser fileChooser = new CustomFileChooser();
                fileChooser.setCurrentDirectory(new File
                        (System.getProperty("user.home") + System.getProperty("file.separator")+ "Downloads"));
                int fileChooserReturnValue = fileChooser.showOpenDialog(demoWindow);
                if (fileChooserReturnValue == JFileChooser.APPROVE_OPTION) {
//                    new Thread(() -> {
//                    });
                    try {
                        File file = fileChooser.getSelectedFile();
                        maxFrames = new Mp3File(file).getFrameCount();
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        device.open(decoder = new Decoder());
                        bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));
//                        bitstream = new Bitstream(array[1].getBufferedInputStream());
                        progressBar.setMaximum(maxFrames);
                    } catch (JavaLayerException | InvalidDataException | UnsupportedTagException | IOException ex) {
                        ex.printStackTrace();
                    }
                    playerThread = new Thread(new DemoTask());
                    playerThread.start();
                }
            };
            chooseFileButton.addActionListener(buttonListener);

            mainPanel.add(progressBar);
            mainPanel.add(buttonPanel);
            this.add(mainPanel);
            this.setVisible(true);
        }

        public void setProgress(int newValue) {
            EventQueue.invokeLater(() -> {
                progressBar.setValue(newValue);
            });
        }
    }

    static class DemoTask implements Runnable {
        @Override
        public void run() {
            if (device != null) {
                try {
                    Header h;
                    int currentFrame = 0;
                    do {
                        h = bitstream.readFrame();
                        SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                        device.write(output.getBuffer(), 0, output.getBufferLength());
                        bitstream.closeFrame();
                        demoWindow.setProgress(currentFrame);
                        currentFrame++;
                    } while (h != null);
                } catch (JavaLayerException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}