package com.aracdefteri.app;
import java.util.*;
/** Component-based top-view body chart reader. No page coordinates, no white=original fallback. */
public final class BodyDiagramReader {
 private static final class Blob {int l,t,r,b,n,state;Blob(int x,int y,int s){l=r=x;t=b=y;state=s;}void add(int x,int y){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);n++;}}
 public static Map<String,String> read(int w,int h,int[] pixels){
  Map<String,String> out=new LinkedHashMap<>();if(w<60||h<60||pixels.length!=w*h)return out;
  byte[] states=new byte[pixels.length];for(int i=0;i<states.length;i++)states[i]=(byte)color(pixels[i]);
  List<Blob> blobs=new ArrayList<>();int[] queue=new int[pixels.length];
  for(int i=0;i<states.length;i++){
   int state=states[i];if(state==0)continue;Blob blob=new Blob(i%w,i/w,state);int head=0,tail=0;queue[tail++]=i;states[i]=0;
   while(head<tail){int at=queue[head++],x=at%w,y=at/w;blob.add(x,y);int[] neighbors={x>0?at-1:-1,x<w-1?at+1:-1,y>0?at-w:-1,y<h-1?at+w:-1};for(int q:neighbors)if(q>=0&&states[q]==state){states[q]=0;queue[tail++]=q;}}
   if(blob.n>w*h*.0008 && (blob.r-blob.l+1)<(blob.b-blob.t+1)*4)blobs.add(blob);
  }
  int max=0;for(Blob b:blobs)max=Math.max(max,b.n);final int minimum=Math.max(15,(int)(max*.10));blobs.removeIf(b->b.n<minimum);
  if(blobs.size()<3)return out;
  int l=w,t=h,r=0,bottom=0;for(Blob b:blobs){l=Math.min(l,b.l);r=Math.max(r,b.r);t=Math.min(t,b.t);bottom=Math.max(bottom,b.b);}
  float dw=r-l,dh=bottom-t;if(dw<30||dh<30||dw/dh<.65||dw/dh>1.5)return out;
  // The full left/right/front/rear extent must be evidenced. Partial diagrams are left unassigned.
  boolean left=false,right=false,front=false,rear=false,hood=false;
  for(Blob b:blobs){float x=((b.l+b.r)/2f-l)/dw,y=((b.t+b.b)/2f-t)/dh;if(x<.3)left=true;if(x>.7)right=true;if(y<.28)front=true;if(y>.70)rear=true;if(x>.35&&x<.65&&y<.28)hood=true;}
  if(!(left&&right&&front&&rear&&hood))return out;
  String[] names={"Kaput","Sol ön çamurluk","Sağ ön çamurluk","Sol ön kapı","Sağ ön kapı","Sol arka kapı","Sağ arka kapı","Sol arka çamurluk","Sağ arka çamurluk","Tavan","Bagaj kapağı"};
  float[][] center={{.50f,.12f},{.10f,.16f},{.90f,.16f},{.11f,.415f},{.89f,.415f},{.11f,.635f},{.89f,.635f},{.10f,.86f},{.90f,.86f},{.50f,.585f},{.50f,.89f}};
  String[] status={"","Değişen","Lokal boyalı","Boyalı"};
  for(int i=0;i<names.length;i++){
   int cx=Math.round(l+center[i][0]*dw),cy=Math.round(t+center[i][1]*dh);int rx=Math.max(2,Math.round(dw*.065f)),ry=Math.max(2,Math.round(dh*.07f));int[] counts=new int[4];int total=0;
   for(int y=Math.max(0,cy-ry);y<Math.min(h,cy+ry);y++)for(int x=Math.max(0,cx-rx);x<Math.min(w,cx+rx);x++){counts[color(pixels[y*w+x])]++;total++;}
   int winner=0;for(int j=1;j<4;j++)if(counts[j]>counts[winner]||winner==0)winner=j;
   if(total>0&&counts[winner]>total*.18){out.put(names[i],status[winner]);continue;}
   // A dark check at the expected center must be present. Bare white paper is unknown.
   int dark=0,small=0;int rr=Math.max(3,Math.round(dw*.024f));
   for(int y=Math.max(0,cy-rr);y<Math.min(h,cy+rr);y++)for(int x=Math.max(0,cx-rr);x<Math.min(w,cx+rr);x++){int c=pixels[y*w+x];if(((c>>16)&255)<80&&((c>>8)&255)<80&&(c&255)<80)dark++;small++;}
   if(small>0&&dark>small*.045&&dark<small*.45)out.put(names[i],"Orijinal (işaretli)");
  }
  return out;
 }
 static int color(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(r>160&&g<135&&b<135&&r>g*1.4)return 1;if(r>160&&g>140&&b<125)return 2;if(b>110&&b>r*1.10&&b>g*1.10&&r<200)return 3;return 0;}
}
