package com.limegroup.gnutella.metadata;

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.limegroup.gnutella.metadata.audio.reader.ASFParser;
import com.limegroup.gnutella.metadata.audio.reader.AudioDataReader;
import com.limegroup.gnutella.metadata.audio.reader.MP3MetaData;
import com.limegroup.gnutella.metadata.audio.reader.OGGMetaData;
import com.limegroup.gnutella.metadata.audio.reader.WMAMetaData;
import com.limegroup.gnutella.metadata.video.reader.MOVMetaData;
import com.limegroup.gnutella.metadata.video.reader.MPEGMetaData;
import com.limegroup.gnutella.metadata.video.reader.OGMMetaData;
import com.limegroup.gnutella.metadata.video.reader.RIFFMetaData;
import com.limegroup.gnutella.metadata.video.reader.WMVMetaData;
import com.limegroup.gnutella.xml.LimeXMLUtils;

/**
 * Implementation of MetaDataFactory. Returns the appropriate reader/writer for
 * the file type if one exists, null if one does not exist 
 */
public class MetaDataFactoryImpl implements MetaDataFactory {

    private static final Log LOG = LogFactory.getLog(MetaDataFactory.class); 
    
    /** Creates MetaData for the file, if possible. */  
    public MetaReader parse(File f) throws IOException {
        try {
            if (LimeXMLUtils.isSupportedAudioFormat(f))
                return parseAudioFile(f);
            else if (LimeXMLUtils.isSupportedVideoFormat(f))
                return parseVideoMetaData(f);          
            else if (LimeXMLUtils.isSupportedMultipleFormat(f))
                return parseMultipleFormat(f);
        } catch (OutOfMemoryError e) {
            LOG.warn("Ran out of memory while parsing.",e);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }
    
    /** Figures out what kind of MetaData should exist for this file. */
    private MetaReader parseMultipleFormat(File f) throws IOException {
        if(LimeXMLUtils.isASFFile(f)) {
            ASFParser p = new ASFParser(f);
            if(p.hasVideo())
                return new WMVMetaData(p);
            else if(p.hasAudio())
                return new WMAMetaData(p);
        }
        return null;
    }
    
    /**
     * Reads the meta data for the audio file if LimeWire can parse
     * it, otherwise return null if file type is not supported
     */
    private MetaReader parseAudioFile(File f) throws IOException, IllegalArgumentException { 
        if (LimeXMLUtils.isMP3File(f))
            return new MP3MetaData(f);
        if (LimeXMLUtils.isOGGFile(f))
            return new OGGMetaData(f);
        if (LimeXMLUtils.isFLACFile(f))
            return new AudioDataReader(f);
        if (LimeXMLUtils.isM4AFile(f))
            return new AudioDataReader(f);
        if (LimeXMLUtils.isWMAFile(f))
            return new WMAMetaData(f);
        
        return null;
    }
    
    /**
     * Reads the meta data for the video file if LimeWire can parse
     * it, otherwise return null if the file type is not suported
     */
    private MetaReader parseVideoMetaData(File file)
            throws IOException {
        if (LimeXMLUtils.isRIFFFile(file))
            return new RIFFMetaData(file);
        else if (LimeXMLUtils.isOGMFile(file))
            return new OGMMetaData(file);
        else if(LimeXMLUtils.isWMVFile(file))
            return new WMVMetaData(file);
        else if(LimeXMLUtils.isMPEGFile(file))
            return new MPEGMetaData(file);
        else if (LimeXMLUtils.isQuickTimeFile(file))
            return new MOVMetaData(file);
            
        return null;
    }
}
