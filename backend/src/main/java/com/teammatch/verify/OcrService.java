package com.teammatch.verify;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 本地 OCR（RapidOCR，PaddleOCR 中文模型的 Java 版）。图片不发给任何第三方。
 * <p>
 * 引擎第一次用时才加载（解压模型和本地库，大约一秒）。本地库不保证线程安全，所以一次只识别一张。
 * 识别失败不影响提交：返回 null，管理员照样能看原图人工审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ScreenshotProperties props;

    private InferenceEngine engine;
    /** 本地库加载失败（比如换到了没有对应本地库的系统），之后就不再试了 */
    private boolean unavailable;

    /**
     * 识别图片里的文字，一行一个。关掉了 OCR 或者识别失败时返回 null。
     *
     * @param ext png / jpg
     */
    public synchronized List<String> recognize(byte[] image, String ext) {
        if (!props.ocrEnabled() || unavailable) {
            return null;
        }
        Path tmp = null;
        try {
            // 本地库按文件路径读图，写到系统临时目录、用纯英文文件名，避开中文路径的问题
            tmp = Files.createTempFile("teammatch-ocr-", "." + ext);
            Files.write(tmp, image);
            if (engine == null) {
                engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
            }
            OcrResult result = engine.runOcr(tmp.toString());
            return result.getTextBlocks().stream()
                    .map(TextBlock::getText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (LinkageError e) {
            unavailable = true;
            log.warn("OCR 本地库加载失败，之后的截图只能人工审核", e);
            return null;
        } catch (RuntimeException | IOException e) {
            log.warn("OCR 识别失败", e);
            return null;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件删不掉也不影响
                }
            }
        }
    }
}
