/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.qrcode.encoder;

// JAVAPORT: QRCodeMatrix needs to be renamed Matrix and built as a new class.
//
// template <typename T> class Array2D;
// typedef Array2D<int> QRCodeMatrix;
// #include "util/array/array2d-inl.h"

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 */
public final class QRCode {

  // Magic numbers.
  public static final int kMinVersion = 1;
  public static final int kMaxVersion = 40;
  // For matrix width, see 7.3.1 of JISX0510:2004 (p.5).
  public static final int kMinMatrixWidth = 21;  // Version 1
  public static final int kMaxMatrixWidth = 177;  // Version 40 (21 + 4 * (40 -1)).
  public static final int kNumMaskPatterns = 8;

  // See table 3 of JISX0510:2004 (p.16)
  private static final int kNumBitsTable[][] = {
      // NUMERIC  ALPHANUMERIC  8BIT_BYTE  KANJI
      {       10,            9,         8,     8 },  // Version 1-9
      {       12,           11,        16,    10 },  // Version 10-26
      {       14,           13,        16,    12 },  // Version 27-40
  };

  // JAVAPORT: Do not remove trailing slashes yet. There are very likely conflicts with local
  // variables and parameters which will introduce insidious bugs.
  private Mode mode_;
  private ECLevel ec_level_;
  private int version_;
  private int matrix_width_;
  private int mask_pattern_;
  private int num_total_bytes_;
  private int num_data_bytes_;
  private int num_ec_bytes_;
  private int num_rs_blocks_;
  private QRCodeMatrix *matrix_;


  // They call encoding "mode".  The modes are defined in 8.3 of
  // JISX0510:2004 (p.14).  It's unlikely (probably we will not
  // support complicated modes) but if you add an item to this, please
  // also add it to ModeToString(), GetModeCode(),
  // GetNumBitsForLength(), Encoder.AppendBytes(),
  // Encoder.ChooseMode().
  public enum Mode {
    MODE_UNDEFINED = -1,
    MODE_NUMERIC,
    MODE_ALPHANUMERIC,
    MODE_8BIT_BYTE,
    MODE_KANJI,  // Shift_JIS
    // The following modes are unimplemented.
    // MODE_ECI,
    // MODE_MIXED,
    // MODE_CONCATENATED,
    // MODE_FNC1,
    NUM_MODES,  // Always keep this at the end.
  };

  // The error correction levels are defined in the table 22 of
  // JISX0510:2004 (p.45).  It's very unlikely (we've already covered
  // all of them!)  but if you add an item to this, please also add it
  // to ECLevelToString() and GetECLevelCode().
  public enum ECLevel {
    EC_LEVEL_UNDEFINED  = -1,
    // They don't have names in the standard!
    EC_LEVEL_L,  //  7% of corruption can be recovered.
    EC_LEVEL_M,  // 15%
    EC_LEVEL_Q,  // 25%
    EC_LEVEL_H,  // 30%
    NUM_EC_LEVELS,  // Always keep this at the end.
  };

  public QRCode() {
    mode_ = MODE_UNDEFINED;
    ec_level_ = EC_LEVEL_UNDEFINED;
    version_ = -1;
    matrix_width_ = -1;
    mask_pattern_ = -1;
    num_total_bytes_ = -1;
    num_data_bytes_ = -1;
    num_ec_bytes_ = -1;
    num_rs_blocks_ = -1;
    matrix_ = null;
  }

  // Mode of the QR Code.
  public Mode mode() { return mode_; }
  // Error correction level of the QR Code.
  public ECLevel ec_level() { return ec_level_; }
  // Version of the QR Code.  The bigger size, the bigger version.
  public int version() { return version_; }
  // Matrix width of the QR Code.
  public int matrix_width() { return matrix_width_; }
  // Mask pattern of the QR Code.
  public int mask_pattern() { return mask_pattern_; }
  // Number of total bytes in the QR Code.
  public int num_total_bytes() { return num_total_bytes_; }
  // Number of data bytes in the QR Code.
  public int num_data_bytes() { return num_data_bytes_; }
  // Number of error correction bytes in the QR Code.
  public int num_ec_bytes() { return num_ec_bytes_; }
  // Number of Reedsolomon blocks in the QR Code.
  public int num_rs_blocks() { return num_rs_blocks_; }
  // Matrix data of the QR Code.
  public final QRCodeMatrix* matrix() { return matrix_; }

  // Return the value of the module (cell) pointed by "x" and "y" in
  // the matrix of the QR Code.  They call cells in the matrix
  // "modules".  1 represents a black cell, and 0 represents a white
  // cell.
  //
  // Note that the class internally used Array2D.  You should access
  // cells in row-major order for cache efficiency.  Example:
  //
  //   for (int y = 0; y < qrcode.matrix_width(); ++y) {
  //     for (int x = 0; x < qrcode.matrix_width(); ++x) {
  //       DoSomething(qrcode.at(x, y));
  //     }
  //   }
  //
  public int at(int x, int y) {
    // The value must be zero or one.
    Debug.DCHECK((*matrix_)(y, x) == 0 || (*matrix_)(y, x) == 1);
    return (*matrix_)(y, x);
  }

  // Checks all the member variables are set properly.  Returns true
  // on success.  Otherwise, returns false.
  public boolean IsValid() {
    return (
        // First check if all version are not uninitialized.
        mode_ != MODE_UNDEFINED &&
            ec_level_ != EC_LEVEL_UNDEFINED &&
            version_ != -1 &&
            matrix_width_ != -1 &&
            mask_pattern_ != -1 &&
            num_total_bytes_ != -1 &&
            num_data_bytes_ != -1 &&
            num_ec_bytes_ != -1 &&
            num_rs_blocks_ != -1 &&
            // Then check them in other ways..
            IsValidVersion(version_) &&
            IsValidMode(mode_) &&
            IsValidECLevel(ec_level_) &&
            IsValidMatrixWidth(matrix_width_) &&
            IsValidMaskPattern(mask_pattern_) &&
            num_total_bytes_ == num_data_bytes_ + num_ec_bytes_ &&
            // Matrix stuff.
            matrix_ != null &&
            matrix_width_ == matrix_.width() &&
            // See 7.3.1 of JISX0510:2004 (p.5).
            matrix_width_ == kMinMatrixWidth + (version_ - 1) * 4 &&
            matrix_.width() == matrix_.height() &&  // Must be square.
            EverythingIsBinary(*matrix_));
  }

  // Return debug String.
  public String DebugString() {
    String result;
    StringAppendF(&result, "<<QRCode\n");
    StringAppendF(&result, " mode: %s\n", ModeToString(mode_));
    StringAppendF(&result, " ec_level: %s\n", ECLevelToString(ec_level_));
    StringAppendF(&result, " version: %d\n", version_);
    StringAppendF(&result, " matrix_width: %d\n", matrix_width_);
    StringAppendF(&result, " mask_pattern: %d\n", mask_pattern_);
    StringAppendF(&result, " num_total_bytes_: %d\n", num_total_bytes_);
    StringAppendF(&result, " num_data_bytes: %d\n", num_data_bytes_);
    StringAppendF(&result, " num_ec_bytes: %d\n", num_ec_bytes_);
    StringAppendF(&result, " num_rs_blocks: %d\n", num_rs_blocks_);
    if (matrix_ == null) {
      StringAppendF(&result, " matrix: null\n");
    } else {
      StringAppendF(&result, " matrix:\n%s",
          MatrixUtil.ToASCII(*matrix_).c_str());
    }
    StringAppendF(&result, ">>\n");
    return result;
  }

  public void set_mode(Mode value) { mode_ = value; }
  public void set_ec_level(ECLevel value) { ec_level_ = value; }
  public void set_version(int value) { version_ = value; }
  public void set_matrix_width(int value) { matrix_width_ = value; }
  public void set_mask_pattern(int value) { mask_pattern_ = value; }
  public void set_num_total_bytes(int value) { num_total_bytes_ = value; }
  public void set_num_data_bytes(int value) { num_data_bytes_ = value; }
  public void set_num_ec_bytes(int value) { num_ec_bytes_ = value; }
  public void set_num_rs_blocks(int value) { num_rs_blocks_ = value; }
  // This takes ownership of the 2D array.  The 2D array will be
  // deleted in the destructor of the class.
  public void set_matrix(QRCodeMatrix *value) { matrix_ = value; }


  // Check if "version" is valid.
  public static boolean IsValidVersion(final int version) {
    return version >= kMinVersion && version <= kMaxVersion;
  }
  // Check if "mask_pattern" is valid.
  public static boolean IsValidECLevel(ECLevel ec_level) {
    return ec_level >= 0 && ec_level < NUM_EC_LEVELS;
  }
  // Check if "mode" is valid.
  public static boolean IsValidMode(final QRCode.Mode mode) {
    return mode >= 0 && mode < NUM_MODES;
  }
  // Check if "width" is valid.
  public static boolean IsValidMatrixWidth(int width) {
    return width >= kMinMatrixWidth && width <= kMaxMatrixWidth;
  }
  // Check if "mask_pattern" is valid.
  public static boolean IsValidMaskPattern(int mask_pattern) {
    return mask_pattern >= 0 && mask_pattern < kNumMaskPatterns;
  }

  // Convert "ec_level" to String for debugging.
  public static final char *ECLevelToString(QRCode.ECLevel ec_level) {
  switch (ec_level) {
    case QRCode.EC_LEVEL_UNDEFINED:
      return "UNDEFINED";
    case QRCode.EC_LEVEL_L:
      return "L";
    case QRCode.EC_LEVEL_M:
      return "M";
    case QRCode.EC_LEVEL_Q:
      return "Q";
    case QRCode.EC_LEVEL_H:
      return "H";
    default:
      break;
  }
  return "UNKNOWN";
}

  // Convert "mode" to String for debugging.
  public static final char *ModeToString(QRCode.Mode mode) {
  switch (mode) {
    case QRCode.MODE_UNDEFINED:
      return "UNDEFINED";
    case QRCode.MODE_NUMERIC:
      return "NUMERIC";
    case QRCode.MODE_ALPHANUMERIC:
      return "ALPHANUMERIC";
    case QRCode.MODE_8BIT_BYTE:
      return "8BIT_BYTE";
    case QRCode.MODE_KANJI:
      return "KANJI";
    default:
      break;
  }
  return "UNKNOWN";
}

  // Return the code of error correction level.  On error, return -1.
  // The codes of error correction levels are defined in the table 22
  // of JISX0510:2004 (p.45).
  public static int GetECLevelCode(final QRCode.ECLevel ec_level) {
    switch (ec_level) {
      case QRCode.EC_LEVEL_L:
        return 1;
      case QRCode.EC_LEVEL_M:
        return 0;
      case QRCode.EC_LEVEL_Q:
        return 3;
      case QRCode.EC_LEVEL_H:
        return 2;
      default:
        break;
    }
    return -1;  // Unknown error correction level.
  }

  // Return the code of mode.  On error, return -1.
  // The codes of modes are defined in the table 2 of JISX0510:2004
  // (p.16).
  public static int GetModeCode(final QRCode.Mode mode) {
    switch (mode) {
      case QRCode.MODE_NUMERIC:
        return 1;
      case QRCode.MODE_ALPHANUMERIC:
        return 2;
      case QRCode.MODE_8BIT_BYTE:
        return 4;
      case QRCode.MODE_KANJI:
        return 8;
      default:
        break;
    }
    return -1;  // Unknown mode.
  }

  // Return the number of bits needed for representing the length info
  // of QR Code with "version" and "mode".  On error, return -1.
  public static int GetNumBitsForLength(int version, QRCode.Mode mode) {
    if (!IsValidVersion(version)) {
      Debug.LOG_ERROR("Invalid version: " + version);
      return -1;
    }
    if (!IsValidMode(mode)) {
      Debug.LOG_ERROR("Invalid mode: " + mode);
      return -1;
    }
    if (version >= 1 && version <= 9) {
      return kNumBitsTable[0][mode];
    } else if (version >= 10 && version <= 26) {
      return kNumBitsTable[1][mode];
    } else if (version >= 27 && version <= 40) {
      return kNumBitsTable[2][mode];
    } else {
      Debug.LOG_ERROR("Should not reach");
    }
    return -1;
  }

  // Return true if the all values in the matrix are binary numbers.
  // Otherwise, return false.
  private static boolean EverythingIsBinary(final Array2D<int> &matrix) {
    for (int y = 0; y < matrix.height(); ++y) {
      for (int x = 0; x < matrix.width(); ++x) {
        if (!(matrix(y, x) == 0 || matrix(y, x) == 1)) {
          // Found non zero/one value.
          return false;
        }
      }
    }
    return true;
  }

}
