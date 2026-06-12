package com.sonograma.service;

import com.sonograma.dto.PedidoUploadResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PedidoControlImportService {

    private final PedidoService pedidoService;
    private final InvoiceControlWorkbookService workbookService;

    public record Result(Long pedidoId, InvoiceControlWorkbookService.GeneratedWorkbook workbook) {}

    @Transactional
    public Result importAndGenerate(MultipartFile pdf, MultipartFile template) {
        PedidoUploadResponseDTO upload = pedidoService.crearDesdePdf(pdf);
        return new Result(upload.pedidoId(), workbookService.generate(upload.pedidoId(), template));
    }
}
