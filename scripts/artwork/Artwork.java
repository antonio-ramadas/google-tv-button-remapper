import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original vector artwork rendered with the JDK; no third-party graphics libraries. */
public final class Artwork {
    private static final Color BG = new Color(0x101920), TEAL = new Color(0x173a3b);
    private static final Color MINT = new Color(0x8ce5ce), INK = new Color(0xf0f6f7);
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }
    private static void remote(Graphics2D g, double x, double y, double scale) {
        Graphics2D r = (Graphics2D) g.create(); r.translate(x,y); r.scale(scale,scale);
        r.setColor(MINT); r.fill(new RoundRectangle2D.Double(28,12,40,72,16,16));
        r.setColor(TEAL); r.fillRect(42,26,12,12); r.fillRect(38,52,20,6); r.fillRect(38,65,20,6);
        r.dispose();
    }
    private static void label(Graphics2D g, String text, int size, int x, int y, Color color) {
        g.setFont(new Font("DejaVu Sans",Font.BOLD,size)); g.setColor(color); g.drawString(text,x,y);
    }
    private static void save(BufferedImage image, String path) throws Exception {
        File file = new File(path); file.getParentFile().mkdirs(); ImageIO.write(image,"png",file);
        System.out.println(path + " " + image.getWidth() + "x" + image.getHeight());
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            BufferedImage input=ImageIO.read(new File(args[0]));
            BufferedImage rgb=new BufferedImage(input.getWidth(),input.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=graphics(rgb); g.drawImage(input,0,0,null); g.dispose(); save(rgb,args[1]); return;
        }
        BufferedImage icon = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=graphics(icon); g.setColor(TEAL); g.fillRect(0,0,512,512); remote(g,0,0,512.0/96); g.dispose();
        save(icon,"store/graphics/icon-512.png");
        for (int[] spec : new int[][]{{1024,500},{1280,720},{320,180}}) {
            int w=spec[0],h=spec[1];
            BufferedImage art=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); g=graphics(art);
            g.scale(w/1280.0,h/720.0); g.setColor(BG); g.fillRect(0,0,1280,720);
            g.setColor(TEAL); g.fillOval(820,-130,650,980);
            remote(g,830,145,4.5);
            label(g,"Button",90,85,315,INK); label(g,"Remapper",90,85,420,INK);
            if (w>320) label(g,"YOUR REMOTE. YOUR WAY.",25,90,495,MINT);
            g.dispose();
            save(art,w==1024?"store/graphics/feature-1024x500.png":w==1280?"store/graphics/tv-banner-1280x720.png":"app/res/drawable-xhdpi/banner.png");
        }
    }
}
