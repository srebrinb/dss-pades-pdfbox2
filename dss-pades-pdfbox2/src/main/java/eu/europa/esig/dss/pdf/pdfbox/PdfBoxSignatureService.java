/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 *
 * This file is part of the "DSS - Digital Signature Services" project.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.pdf.pdfbox;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigProperties;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.DSSUtils;
import eu.europa.esig.dss.DigestAlgorithm;
import eu.europa.esig.dss.InMemoryDocument;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.visible.ImageFactory;
import eu.europa.esig.dss.pdf.DSSPDFUtils;
import eu.europa.esig.dss.pdf.PDFSignatureService;
import eu.europa.esig.dss.pdf.PdfDict;
import eu.europa.esig.dss.pdf.PdfDssDict;
import eu.europa.esig.dss.pdf.PdfSignatureOrDocTimestampInfo;
import eu.europa.esig.dss.pdf.PdfSignatureOrDocTimestampInfoComparator;
import eu.europa.esig.dss.pdf.SignatureValidationCallback;
import eu.europa.esig.dss.pdf.model.ModelPdfDict;
import eu.europa.esig.dss.x509.CertificatePool;
import eu.europa.esig.dss.x509.CertificateToken;
import java.io.ByteArrayOutputStream;

class PdfBoxSignatureService implements PDFSignatureService {

    private static final Logger logger = LoggerFactory.getLogger(PdfBoxSignatureService.class);

    @Override
    public byte[] digest(final InputStream toSignDocument, final PAdESSignatureParameters parameters, final DigestAlgorithm digestAlgorithm) throws DSSException {

        final byte[] signatureValue = DSSUtils.EMPTY_BYTE_ARRAY;
        
        PDDocument pdDocument = null;
        final ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
        try {

         //   toSignFile = DSSPDFUtils.getFileFromPdfData(toSignDocument);
            byte[] toSignBytes=IOUtils.toByteArray(toSignDocument);
            pdDocument = PDDocument.load(new ByteArrayInputStream(toSignBytes));
            PDSignature pdSignature = createSignatureDictionary(parameters);

           File signedFile = null;//File.createTempFile("sd-dss-", "-signed.pdf");
            //final FileOutputStream fileOutputStream = DSSPDFUtils.getFileOutputStream(toSignFile, signedFile);
            
            
            final byte[] digestValue = signDocumentAndReturnDigest(parameters, signatureValue,  fileOutputStream, pdDocument, pdSignature, digestAlgorithm);
            fileOutputStream.close();
            return digestValue;
        } catch (IOException e) {
            throw new DSSException(e);
        } finally {
      //      DSSUtils.delete(signedFile);
     
            IOUtils.closeQuietly(pdDocument);
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    @Override
    public void sign(final InputStream pdfData, final byte[] signatureValue, final OutputStream signedStream, final PAdESSignatureParameters parameters,
            final DigestAlgorithm digestAlgorithm) throws DSSException {

      //  File toSignFile = null;
      //  File signedFile = null;
      //  FileInputStream fileInputStream = null;
      //  FileInputStream finalFileInputStream = null;
        PDDocument pdDocument = null;
        final ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
        try {

       //    toSignFile = DSSPDFUtils.getFileFromPdfData(pdfData);
            byte[] toSignBytes=IOUtils.toByteArray(pdfData);
            pdDocument = PDDocument.load(new ByteArrayInputStream(toSignBytes));
            
            final PDSignature pdSignature = createSignatureDictionary(parameters);

          //  signedFile = File.createTempFile("sd-dss-", "-signedSign.pdf");

            //final FileOutputStream fileOutputStream = DSSPDFUtils.getFileOutputStream(toSignFile, signedFile);
         //   final FileOutputStream fileOutputStream = new FileOutputStream(signedFile);
            
            signDocumentAndReturnDigest(parameters, signatureValue,  fileOutputStream, pdDocument, pdSignature, digestAlgorithm);            
            IOUtils.write(fileOutputStream.toByteArray(), signedStream);
          //  finalFileInputStream = new FileInputStream(signedFile);
          //  IOUtils.copy(finalFileInputStream, signedStream);
        } catch (IOException e) {
            throw new DSSException(e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
         //   IOUtils.closeQuietly(finalFileInputStream);
         //   DSSUtils.delete(toSignFile);
           // DSSUtils.delete(signedFile);
            IOUtils.closeQuietly(pdDocument);
        }
    }

    private byte[] signDocumentAndReturnDigest(final PAdESSignatureParameters parameters, final byte[] signatureBytes, 
            final OutputStream fileOutputStream, final PDDocument pdDocument, final PDSignature pdSignature, final DigestAlgorithm digestAlgorithm) throws DSSException {

        try {

            final MessageDigest digest = DSSUtils.getMessageDigest(digestAlgorithm);
            // register signature dictionary and sign interface
            SignatureInterface signatureInterface = new SignatureInterface() {

                @Override
                public byte[] sign(InputStream content) throws IOException {

                    byte[] b = new byte[4096];
                    int count;
                    while ((count = content.read(b)) > 0) {
                        digest.update(b, 0, count);
                    }
                    return signatureBytes;
                }
            };

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(parameters.getSignatureSize());

            if (parameters.getImageParameters() != null) {
                fillImageParameters(pdDocument, parameters.getImageParameters(), options);
            }
            pdDocument.addSignature(pdSignature, signatureInterface, options);

            saveDocumentIncrementally(parameters,  fileOutputStream, pdDocument);
            final byte[] digestValue = digest.digest();
            if (logger.isDebugEnabled()) {
                logger.debug("Digest to be signed: " + Hex.encodeHexString(digestValue));
            }
            fileOutputStream.close();
            return digestValue;
        } catch (IOException e) {
            throw new DSSException(e);
        }
    }

    private void fillImageParameters(final PDDocument doc, final SignatureImageParameters imgParams, SignatureOptions options) throws IOException {
        Dimension optimalSize = ImageFactory.getOptimalSize(imgParams);
        PDVisibleSignDesigner visibleSig = new PDVisibleSignDesigner(doc, ImageFactory.create(imgParams), imgParams.getPage());
        visibleSig.xAxis(imgParams.getxAxis()).yAxis(imgParams.getyAxis()).width((float) optimalSize.getWidth()).height((float) optimalSize.getHeight());

        PDVisibleSigProperties signatureProperties = new PDVisibleSigProperties();
        signatureProperties.visualSignEnabled(true).setPdVisibleSignature(visibleSig).buildSignature();

        options.setVisualSignature(signatureProperties);
        options.setPage(imgParams.getPage());
    }

    private PDSignature createSignatureDictionary(final PAdESSignatureParameters parameters) {

        final PDSignature signature = new PDSignature();
        signature.setType(getType());
//		signature.setName(String.format("SD-DSS Signature %s", parameters.getDeterministicId()));
        Date date = parameters.bLevel().getSigningDate();
        String encodedDate = " " + Hex.encodeHexString(DSSUtils.digest(DigestAlgorithm.SHA1, Long.toString(date.getTime()).getBytes()));
        CertificateToken token = parameters.getSigningCertificate();
        if (token == null) {
            signature.setName("Unknown signer" + encodedDate);
        } else if (parameters.getSigningCertificate().getSubjectShortName() != null) {
            String shortName = parameters.getSigningCertificate().getSubjectShortName() + encodedDate;
            signature.setName(shortName);
        } else {
            signature.setName("Unknown signer" + encodedDate);
        }

        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE); // default filter
        // sub-filter for basic and PAdES Part 2 signatures
        signature.setSubFilter(getSubFilter());

        if (COSName.SIG.equals(getType())) {
            if (StringUtils.isNotEmpty(parameters.getContactInfo())) {
                signature.setContactInfo(parameters.getContactInfo());
            }

            if (StringUtils.isNotEmpty(parameters.getLocation())) {
                signature.setLocation(parameters.getLocation());
            }

            if (StringUtils.isNotEmpty(parameters.getReason())) {
                signature.setReason(parameters.getReason());
            }
        }

        // the signing date, needed for valid signature
        final Calendar cal = Calendar.getInstance();
        final Date signingDate = parameters.bLevel().getSigningDate();
        cal.setTime(signingDate);
        signature.setSignDate(cal);
        return signature;
    }

    protected COSName getType() {
        return COSName.SIG;
    }

    public void saveDocumentIncrementally(PAdESSignatureParameters parameters, OutputStream fileOutputStream, PDDocument pdDocument) throws DSSException {

        //FileInputStream signedFileInputStream = null;
        try {

           // signedFileInputStream = new FileInputStream(signedFile);
            // the document needs to have an ID, if not a ID based on the current system time is used, and then the digest of the signed data is
            // different
            if (pdDocument.getDocumentId() == null) {

                final byte[] documentIdBytes = DSSUtils.digest(DigestAlgorithm.MD5, parameters.bLevel().getSigningDate().toString().getBytes());
                pdDocument.setDocumentId(DSSUtils.toLong(documentIdBytes));
                pdDocument.setDocumentId(0L);
            }
            pdDocument.saveIncremental(fileOutputStream);
        } catch (IOException e) {
            throw new DSSException(e);
        } finally {
           // IOUtils.closeQuietly(signedFileInputStream);
        }
    }

    protected COSName getSubFilter() {
        return PDSignature.SUBFILTER_ETSI_CADES_DETACHED;
    }

    @Override
    public void validateSignatures(CertificatePool validationCertPool, DSSDocument document, SignatureValidationCallback callback) throws DSSException {
        // recursive search of signature
        InputStream inputStream = document.openStream();
        try {
            List<PdfSignatureOrDocTimestampInfo> signaturesFound = getSignatures(validationCertPool, IOUtils.toByteArray(inputStream));
            for (PdfSignatureOrDocTimestampInfo pdfSignatureOrDocTimestampInfo : signaturesFound) {
                callback.validate(pdfSignatureOrDocTimestampInfo);
            }
        } catch (IOException e) {
            logger.error("Cannot validate signatures : " + e.getMessage(), e);
        }

        IOUtils.closeQuietly(inputStream);
    }

    private List<PdfSignatureOrDocTimestampInfo> getSignatures(CertificatePool validationCertPool, byte[] originalBytes) {
        List<PdfSignatureOrDocTimestampInfo> signatures = new ArrayList<PdfSignatureOrDocTimestampInfo>();
        ByteArrayInputStream bais = null;
        PDDocument doc = null;
        try {

            bais = new ByteArrayInputStream(originalBytes);
            doc = PDDocument.load(bais);
            List<PDSignature> pdSignatures = doc.getSignatureDictionaries();
            if (CollectionUtils.isNotEmpty(pdSignatures)) {
                logger.debug("{} signature(s) found", pdSignatures.size());

                PdfDict catalog = new PdfBoxDict(doc.getDocumentCatalog().getCOSObject(), doc);
                PdfDssDict dssDictionary = PdfDssDict.build(catalog);

                for (PDSignature signature : pdSignatures) {
                    String subFilter = signature.getSubFilter();
                    byte[] cms = signature.getContents(originalBytes);

                    if (StringUtils.isEmpty(subFilter) || ArrayUtils.isEmpty(cms)) {
                        logger.warn("Wrong signature with empty subfilter or cms.");
                        continue;
                    }

                    byte[] signedContent = signature.getSignedContent(originalBytes);
                    int[] byteRange = signature.getByteRange();

                    PdfSignatureOrDocTimestampInfo signatureInfo = null;
                    if (PdfBoxDocTimeStampService.SUB_FILTER_ETSI_RFC3161.getName().equals(subFilter)) {
                        boolean isArchiveTimestamp = false;

                        // LT or LTA
                        if (dssDictionary != null) {
                            // check is DSS dictionary already exist
                            if (isDSSDictionaryPresentInPreviousRevision(getOriginalBytes(byteRange, signedContent))) {
                                isArchiveTimestamp = true;
                            }
                        }

                        signatureInfo = new PdfBoxDocTimestampInfo(validationCertPool, signature, dssDictionary, cms, signedContent, isArchiveTimestamp);
                    } else {
                        signatureInfo = new PdfBoxSignatureInfo(validationCertPool, signature, dssDictionary, cms, signedContent);
                    }

                    if (signatureInfo != null) {
                        signatures.add(signatureInfo);
                    }
                }
                Collections.sort(signatures, new PdfSignatureOrDocTimestampInfoComparator());
                linkSignatures(signatures);

                for (PdfSignatureOrDocTimestampInfo sig : signatures) {
                    logger.debug("Signature " + sig.uniqueId() + " found with byteRange " + Arrays.toString(sig.getSignatureByteRange()) + " (" + sig.getSubFilter() + ")");
                }
            }

        } catch (Exception e) {
            logger.warn("Cannot analyze signatures : " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(bais);
            IOUtils.closeQuietly(doc);
        }

        return signatures;
    }

    /**
     * This method links previous signatures to the new one. This is useful to
     * get revision number and to know if a TSP is over the DSS dictionary
     */
    private void linkSignatures(List<PdfSignatureOrDocTimestampInfo> signatures) {
        List<PdfSignatureOrDocTimestampInfo> previousList = new ArrayList<PdfSignatureOrDocTimestampInfo>();
        for (PdfSignatureOrDocTimestampInfo sig : signatures) {
            if (CollectionUtils.isNotEmpty(previousList)) {
                for (PdfSignatureOrDocTimestampInfo previous : previousList) {
                    previous.addOuterSignature(sig);
                }
            }
            previousList.add(sig);
        }
    }

    private boolean isDSSDictionaryPresentInPreviousRevision(byte[] originalBytes) {
        ByteArrayInputStream bais = null;
        PDDocument doc = null;
        PdfDssDict dssDictionary = null;
        try {
            bais = new ByteArrayInputStream(originalBytes);
            doc = PDDocument.load(bais);
            List<PDSignature> pdSignatures = doc.getSignatureDictionaries();
            if (CollectionUtils.isNotEmpty(pdSignatures)) {
                PdfDict catalog = new PdfBoxDict(doc.getDocumentCatalog().getCOSObject(), doc);
                dssDictionary = PdfDssDict.build(catalog);
            }
        } catch (Exception e) {
            logger.warn("Cannot check in previous revisions if DSS dictionary already exist : " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(bais);
            IOUtils.closeQuietly(doc);
        }

        return dssDictionary != null;
    }

    private byte[] getOriginalBytes(int[] byteRange, byte[] signedContent) {
        final int length = byteRange[1];
        final byte[] result = new byte[length];
        System.arraycopy(signedContent, 0, result, 0, length);
        return result;
    }

    @Override
    public void addDssDictionary(InputStream inputStream, OutputStream outpuStream, ModelPdfDict dssDictionary) {
       // FileInputStream fis = null;
        PDDocument pdDocument = null;
  //      File signedFile=null;
        try {

           // File toSignFile = DSSPDFUtils.getFileFromPdfData(inputStream);

          //  pdDocument = PDDocument.load(toSignFile);
            byte[] toSignBytes=IOUtils.toByteArray(inputStream);
            pdDocument = PDDocument.load(new ByteArrayInputStream(toSignBytes));
            
       //     signedFile = File.createTempFile("sd-dss-", "-signedaddDssDictionary.pdf");
        //    signedFile.deleteOnExit();
            // final FileOutputStream fileOutputStream = DSSPDFUtils.getFileOutputStream(toSignFile, signedFile);            
            final ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
            if (dssDictionary != null) {
                COSDictionary cosDictionary = pdDocument.getDocumentCatalog().getCOSObject();

                PdfBoxDict value = new PdfBoxDict(dssDictionary);
                //cosDictionary.setItem("DSS", value.getWrapped());
                cosDictionary.setItem("DSS", value.getWrapped());
                cosDictionary.setNeedToBeUpdated(true);

            }
            if (pdDocument.getDocumentId() == null) {
                pdDocument.setDocumentId(0L);
            }

            pdDocument.saveIncremental(fileOutputStream);

         //   fis = new FileInputStream(signedFile);
            IOUtils.write(fileOutputStream.toByteArray() , outpuStream);

        } catch (Exception e) {
            e.printStackTrace();
            throw new DSSException(e);
        } finally {
            //signedFile.delete();
            IOUtils.closeQuietly(pdDocument);
          //  IOUtils.closeQuietly(fis);
        }
    }

}
