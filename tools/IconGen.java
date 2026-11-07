import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Génère l'icône PNG de Frogdoku (les icônes XML vectorielles ne sont pas lues par F-Droid).
 * Usage : java IconGen <dossier res> <dossier fastlane images>
 */
public class IconGen {
    public static void main(String[] a) throws Exception {
        String res = a[0];
        String[][] sizes = {{"mdpi", "48"}, {"hdpi", "72"}, {"xhdpi", "96"}, {"xxhdpi", "144"}, {"xxxhdpi", "192"}};
        for (String[] s : sizes) {
            File dir = new File(res, "mipmap-" + s[0]);
            dir.mkdirs();
            ImageIO.write(render(Integer.parseInt(s[1])), "png", new File(dir, "ic_launcher.png"));
        }
        if (a.length > 1) {
            File f = new File(a[1]);
            f.mkdirs();
            ImageIO.write(render(512), "png", new File(f, "icon.png"));
        }
    }

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 100.0, size / 100.0);

        // fond : carré arrondi bleu « étang » avec dégradé et reflet
        RoundRectangle2D bg = new RoundRectangle2D.Double(4, 4, 92, 92, 26, 26);
        g.setPaint(new GradientPaint(0, 4, new Color(0x8FDcF5), 0, 96, new Color(0x2E95E0)));
        g.fill(bg);
        g.setPaint(new Color(255, 255, 255, 50));
        g.fill(new Ellipse2D.Double(14, 10, 72, 34));
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(255, 255, 255, 200));
        g.draw(bg);

        // nénuphar
        g.setColor(new Color(0x4CAF50));
        g.fill(new Arc2D.Double(16, 64, 68, 24, -60, 330, Arc2D.PIE));
        g.setColor(new Color(0x2E7D32));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Arc2D.Double(16, 64, 68, 24, -60, 330, Arc2D.PIE));

        Color body = new Color(0x6CCB5F), dark = new Color(0x2A6A2C), belly = new Color(0xEAF8C8);
        BasicStroke outline = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        // corps
        Ellipse2D bodyShape = new Ellipse2D.Double(28, 44, 44, 34);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(44, 52), 30,
                new float[]{0f, 1f}, new Color[]{new Color(0xA6E784), new Color(0x4FA845)}));
        g.fill(bodyShape);
        g.setColor(dark);
        g.setStroke(outline);
        g.draw(bodyShape);
        g.setColor(belly);
        g.fill(new Ellipse2D.Double(40, 58, 20, 17));

        // yeux
        for (int side = -1; side <= 1; side += 2) {
            double ex = 50 + side * 12, ey = 42;
            Ellipse2D cap = new Ellipse2D.Double(ex - 10, ey - 10, 20, 20);
            g.setColor(body);
            g.fill(cap);
            g.setColor(dark);
            g.draw(cap);
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(ex - 7, ey - 7, 14, 14));
            g.setColor(new Color(0x17211C));
            g.fill(new Ellipse2D.Double(ex - 4.2 - side * 0.8, ey - 3.5, 8.4, 8.4));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(ex - 3.5 - side * 0.8, ey - 3.2, 3.2, 3.2));
        }
        // joues + sourire
        g.setColor(new Color(255, 127, 154, 110));
        g.fill(new Ellipse2D.Double(31, 54, 8, 5));
        g.fill(new Ellipse2D.Double(61, 54, 8, 5));
        g.setColor(dark);
        g.draw(new Arc2D.Double(42, 50, 16, 10, 200, 140, Arc2D.OPEN));
        g.dispose();
        return img;
    }
}
