package com.sonograma.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

@Component
public class QRCodeGenerator {

    private static final int QR_SIZE = 300;

    public BufferedImage generarQR(String contenido) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (WriterException e) {
            throw new RuntimeException("Error generando imagen QR", e);
        }
    }

    public byte[] generarQRBytes(String contenido) {
        try {
            BufferedImage image = generarQR(contenido);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error convirtiendo QR a bytes", e);
        }
    }
}
