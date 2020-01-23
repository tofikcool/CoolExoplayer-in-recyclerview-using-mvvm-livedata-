
package com.tofik.coolexoplayer.exoplayer.cool.media;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.FloatRange;


public final class VolumeInfo implements Parcelable {

  // Indicate that the playback is in muted state or not.
  private boolean mute;
  // The actual Volume value if 'mute' is false.
  @FloatRange(from = 0, to = 1) private float volume;

  public VolumeInfo(boolean mute, @FloatRange(from = 0, to = 1) float volume) {
    this.mute = mute;
    this.volume = volume;
  }

  public VolumeInfo(VolumeInfo other) {
    this(other.isMute(), other.getVolume());
  }

  public boolean isMute() {
    return mute;
  }

  public void setMute(boolean mute) {
    this.mute = mute;
  }

  @FloatRange(from = 0, to = 1) public float getVolume() {
    return volume;
  }

  public void setVolume(@FloatRange(from = 0, to = 1) float volume) {
    this.volume = volume;
  }

  public void setTo(boolean mute, @FloatRange(from = 0, to = 1) float volume) {
    this.mute = mute;
    this.volume = volume;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByte(this.mute ? (byte) 1 : (byte) 0);
    dest.writeFloat(this.volume);
  }

  protected VolumeInfo(Parcel in) {
    this.mute = in.readByte() != 0;
    this.volume = in.readFloat();
  }

  public static final Creator<VolumeInfo> CREATOR = new ClassLoaderCreator<VolumeInfo>() {
    @Override
    public VolumeInfo createFromParcel(Parcel source, ClassLoader loader) {
      return new VolumeInfo(source);
    }

    @Override
    public VolumeInfo createFromParcel(Parcel source) {
      return new VolumeInfo(source);
    }

    @Override
    public VolumeInfo[] newArray(int size) {
      return new VolumeInfo[size];
    }
  };

  @SuppressWarnings("SimplifiableIfStatement") @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VolumeInfo that = (VolumeInfo) o;

    if (mute != that.mute) return false;
    return Float.compare(that.volume, volume) == 0;
  }

  @Override
  public int hashCode() {
    int result = (mute ? 1 : 0);
    result = 31 * result + (volume != +0.0f ? Float.floatToIntBits(volume) : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Vol{" + "mute=" + mute + ", volume=" + volume + '}';
  }
}