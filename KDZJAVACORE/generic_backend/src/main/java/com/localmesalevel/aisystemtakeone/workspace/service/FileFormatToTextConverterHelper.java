package com.localmesalevel.aisystemtakeone.workspace.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class FileFormatToTextConverterHelper {

    private static final Logger logger = LoggerFactory.getLogger(FileFormatToTextConverterHelper.class);

    private FileFormatToTextConverterHelper() {}

    public static String convertToText(byte[] data, String mimeType) {
        if (data == null || data.length == 0) {
            return "";
        }
        String effectiveMime = mimeType != null ? mimeType.toLowerCase() : "";
        if (effectiveMime.contains("text/plain") || effectiveMime.contains("text/markdown") || effectiveMime.endsWith(".txt") || effectiveMime.endsWith(".md")) {
            return new String(data, StandardCharsets.UTF_8);
        }
        String filenameHint = effectiveMime;
        if (effectiveMime.contains("officedocument") || effectiveMime.contains("application/octet-stream")) {
            filenameHint = "document";
        }
        return extractTextWithTika(data, mimeType, filenameHint);
    }

    private static String extractTextWithTika(byte[] data, String mimeType, String filenameHint) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            if (mimeType != null && !mimeType.isBlank()) {
                metadata.set(Metadata.CONTENT_TYPE, mimeType);
            }
            if (filenameHint != null && !filenameHint.isBlank()) {
                metadata.set("resourceName", filenameHint);
            }
            ParseContext context = new ParseContext();
            parser.parse(inputStream, handler, metadata, context);
            String text = handler.toString();
            return text != null ? text : "";
        } catch (Exception e) {
            logger.warn("Tika extraction failed, falling back to UTF-8 string: {}", e.getMessage());
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
