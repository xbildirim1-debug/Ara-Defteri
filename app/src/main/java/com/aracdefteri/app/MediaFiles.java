package com.aracdefteri.app;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.UUID;

final class MediaFiles {
    static Uri uri(Context c,File file){return FileProvider.getUriForFile(c,c.getPackageName()+".files",file);}
    static boolean owned(Context c,String uri){return uri!=null&&uri.startsWith("content://"+c.getPackageName()+".files/");}
    static Uri copy(Context c,Uri source)throws IOException {
        if(owned(c,source.toString()))return source;
        String mime=c.getContentResolver().getType(source);
        String ext="application/pdf".equals(mime)?".pdf":"image/png".equals(mime)?".png":"image/webp".equals(mime)?".webp":".jpg";
        File dir=new File(c.getFilesDir(),"media");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Klasör oluşturulamadı");
        File file=new File(dir,UUID.randomUUID()+ext);
        try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(file)){
            if(in==null)throw new IOException("Dosya okunamadı");byte[] buffer=new byte[32768];int n;long count=0;
            while((n=in.read(buffer))!=-1){count+=n;if(count>100L*1024*1024)throw new IOException("Dosya 100 MB sınırını aşıyor");out.write(buffer,0,n);}
            if(count==0)throw new IOException("Dosya boş");
        }catch(Exception e){file.delete();throw new IOException("Dosya saklanamadı: "+e.getMessage(),e);}
        return uri(c,file);
    }
}
