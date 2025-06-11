package fr.tylwen.satyria.dynashop.web;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.awt.Color;

public class QrCodeRenderer extends MapRenderer {

    private final BufferedImage qrImage;
    private boolean rendered = false;

    public QrCodeRenderer(BufferedImage qrImage) {
        this.qrImage = qrImage;
    }

    @Override
    public void render(MapView mapView, MapCanvas canvas, Player player) {
        if (rendered) return;
        
        // Nettoyer la carte
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, MapPalette.matchColor(255, 255, 255));
            }
        }
        
        // Dessiner le QR code au centre de la carte
        int startX = (128 - qrImage.getWidth()) / 2;
        int startY = (128 - qrImage.getHeight()) / 2;
        
        for (int x = 0; x < qrImage.getWidth(); x++) {
            for (int y = 0; y < qrImage.getHeight(); y++) {
                if (startX + x < 0 || startX + x >= 128 || startY + y < 0 || startY + y >= 128) continue;
                
                int rgb = qrImage.getRGB(x, y);
                Color color = new Color(rgb);
                byte mapColor = MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue());
                canvas.setPixel(startX + x, startY + y, mapColor);
            }
        }
        
        rendered = true;
    }
}