/*
    Copyright (C) 2017 Aseman Team
    http://aseman.co

    Meikade is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Meikade is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package SevenZip.Archive.SevenZip;

import SevenZip.HRESULT;
import SevenZip.Archive.Common.OutStreamWithCRC;
import SevenZip.Archive.IArchiveExtractCallback;
import SevenZip.Archive.IInArchive;

import Common.BoolVector;
import java.io.File;

class FolderOutStream extends java.io.OutputStream {
    OutStreamWithCRC _outStreamWithHashSpec;
    java.io.OutputStream _outStreamWithHash;
    
    ArchiveDatabaseEx _archiveDatabase;
    BoolVector _extractStatuses;
    int _startIndex;
    int _ref2Offset;
    IArchiveExtractCallback _extractCallback;
    boolean _testMode;
    int _currentIndex;
    boolean _fileIsOpen;
   
    long _filePos;
    
    // Adding parent_dir [GAB, OpenLogic 2013-10-28]
    File parent_dir;

    // Updated to include parent_dir argument [GAB, OpenLogic 2013-10-28]
    public FolderOutStream(String parent) {
        parent_dir = new File(parent);
        _outStreamWithHashSpec = new OutStreamWithCRC();
        _outStreamWithHash = _outStreamWithHashSpec;
    }

    public FolderOutStream() {
        _outStreamWithHashSpec = new OutStreamWithCRC();
        _outStreamWithHash = _outStreamWithHashSpec;
    }
    
    public int Init(
            ArchiveDatabaseEx archiveDatabase,
            int ref2Offset,
            int startIndex,
            BoolVector extractStatuses,
            IArchiveExtractCallback extractCallback,
            boolean testMode) throws java.io.IOException {
        _archiveDatabase = archiveDatabase;
        _ref2Offset = ref2Offset;
        _startIndex = startIndex;
        
        _extractStatuses = extractStatuses;
        _extractCallback = extractCallback;
        _testMode = testMode;
        
        _currentIndex = 0;
        _fileIsOpen = false;
        return WriteEmptyFiles();
    }
    
    // Updated to accept parent_dir argument [GAB, OpenLogic 2013-10-28]
    int OpenFile(java.io.File parent_dir) throws java.io.IOException {
        int askMode;
        if(_extractStatuses.get(_currentIndex))
            askMode = _testMode ?
                IInArchive.NExtract_NAskMode_kTest :
                IInArchive.NExtract_NAskMode_kExtract;
        else
            askMode = IInArchive.NExtract_NAskMode_kSkip;
        
        
        int index = _startIndex + _currentIndex;
        
        // RINOK(_extractCallback->GetStream(_ref2Offset + index, &realOutStream, askMode));
        java.io.OutputStream [] realOutStream2 = new java.io.OutputStream[1]; // TBD

        // Updated to pass parent_dir argument [GAB, OpenLogic 2013-10-28]
        int ret = _extractCallback.GetStream(_ref2Offset + index, realOutStream2, askMode, parent_dir);
        if (ret != HRESULT.S_OK) return ret;
         
        java.io.OutputStream realOutStream = realOutStream2[0];
        
        _outStreamWithHashSpec.SetStream(realOutStream);
        _outStreamWithHashSpec.Init();
        if (askMode == IInArchive.NExtract_NAskMode_kExtract &&
                (realOutStream == null)) {
            FileItem fileInfo = _archiveDatabase.Files.get(index);
            if (!fileInfo.IsAnti && !fileInfo.IsDirectory)
                askMode = IInArchive.NExtract_NAskMode_kSkip;
        }
        return _extractCallback.PrepareOperation(askMode);
    }
    
    int WriteEmptyFiles() throws java.io.IOException {
        for(;_currentIndex < _extractStatuses.size(); _currentIndex++) {
            int index = _startIndex + _currentIndex;
            FileItem fileInfo = _archiveDatabase.Files.get(index);
            if (!fileInfo.IsAnti && !fileInfo.IsDirectory && fileInfo.UnPackSize != 0)
                return HRESULT.S_OK;
            int res = OpenFile(parent_dir);
            if (res != HRESULT.S_OK) return res;
            
            res = _extractCallback.SetOperationResult(
                    IInArchive.NExtract_NOperationResult_kOK);
            if (res != HRESULT.S_OK) return res;
            _outStreamWithHashSpec.ReleaseStream();
        }
        return HRESULT.S_OK;
    }
    
    public void write(int b) throws java.io.IOException {
        throw new java.io.IOException("FolderOutStream - write() not implemented");
    }
    
    public void write(byte [] data,int off,  int size) throws java.io.IOException //  UInt32 *processedSize
    {
        int realProcessedSize = 0;
        while(_currentIndex < _extractStatuses.size()) {
            if (_fileIsOpen) {
                int index = _startIndex + _currentIndex;
                FileItem fileInfo = _archiveDatabase.Files.get(index);
                long fileSize = fileInfo.UnPackSize;
                
                long numBytesToWrite2 = (int)(fileSize - _filePos);
                int tmp = size - realProcessedSize;
                if (tmp < numBytesToWrite2) numBytesToWrite2 = tmp;
                
                int numBytesToWrite = (int)numBytesToWrite2;
                
                int processedSizeLocal;
                // int res = _outStreamWithHash.Write((const Byte *)data + realProcessedSize,numBytesToWrite, &processedSizeLocal));
                // if (res != HRESULT.S_OK) throw new java.io.IOException("_outStreamWithHash.Write : " + res); // return res;
                processedSizeLocal = numBytesToWrite;
                _outStreamWithHash.write(data,realProcessedSize + off,numBytesToWrite);
                
                _filePos += processedSizeLocal;
                realProcessedSize += processedSizeLocal;

                if (_filePos == fileSize) {
                    boolean digestsAreEqual;
                    if (fileInfo.IsFileCRCDefined)
                        digestsAreEqual = (fileInfo.FileCRC == _outStreamWithHashSpec.GetCRC());
                    else
                        digestsAreEqual = true;

                    int res = _extractCallback.SetOperationResult(
                            digestsAreEqual ?
                                IInArchive.NExtract_NOperationResult_kOK :
                                IInArchive.NExtract_NOperationResult_kCRCError);
                    if (res != HRESULT.S_OK) throw new java.io.IOException("_extractCallback.SetOperationResult : " + res); // return res;
                    
                    _outStreamWithHashSpec.ReleaseStream();
                    _fileIsOpen = false;
                    _currentIndex++;
                }
                if (realProcessedSize == size) {
                    int res = WriteEmptyFiles();
                    if (res != HRESULT.S_OK) throw new java.io.IOException("WriteEmptyFiles : " + res); // return res;
                    return ;// return realProcessedSize;
                }
            } else {
              // Updated to pass parent_dir argument [GAB, OpenLogic 2013-10-28]
                int res = OpenFile(parent_dir);
                if (res != HRESULT.S_OK) throw new java.io.IOException("OpenFile : " + res); // return res;
                _fileIsOpen = true;
                _filePos = 0;
            }
        }
        
        // return size;
    }
    
    public int FlushCorrupted(int resultEOperationResult) throws java.io.IOException {
        while(_currentIndex < _extractStatuses.size()) {
            if (_fileIsOpen) {
                int res = _extractCallback.SetOperationResult(resultEOperationResult);
                if (res != HRESULT.S_OK) return res;
                
                _outStreamWithHashSpec.ReleaseStream();
                _fileIsOpen = false;
                _currentIndex++;
            } else {
              // Updated to include parent_dir argument [GAB, OpenLogic 2013-10-28]
                int res = OpenFile(parent_dir);
                if (res != HRESULT.S_OK) return res;
                _fileIsOpen = true;
            }
        }
        return HRESULT.S_OK;
    }
    
    public int WasWritingFinished() {
        int val = _extractStatuses.size();
        if (_currentIndex == val)
            return HRESULT.S_OK;
        return HRESULT.E_FAIL;
    }
    
}
