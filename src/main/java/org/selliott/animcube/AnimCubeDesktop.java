package org.selliott.animcube;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.net.URL;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Standalone desktop launcher for AnimCube on modern JDKs.
 */
public final class AnimCubeDesktop {
    private AnimCubeDesktop() {
    }

    public static void main(final String[] args) throws Exception {
        final String configPath = (args.length > 0) ? args[0]
                : "resources/animcube/AnimCube.cfg";
        final URL baseUrl = new File(".").getCanonicalFile().toURI().toURL();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final AnimCube cube = new AnimCube();
                cube.setStandaloneDocumentBase(baseUrl);
                cube.setStandaloneParameter("config", configPath);

                final JFrame frame = new JFrame("AnimCube");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());
                cube.setPreferredSize(new Dimension(640, 640));
                frame.add(cube, BorderLayout.CENTER);

                cube.init();
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
