package com.aracdefteri.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Small native line icons; no bitmap scaling or font-dependent symbols. */
final class ModuleIcon extends View {
    private final String module;private final Paint p=new Paint(3);private final int color;
    ModuleIcon(Context c,String module,int color){super(c);this.module=module;this.color=color;setContentDescription(module);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/32f,getHeight()/32f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(module){
            case "Lastik":c.drawCircle(16,16,10,p);c.drawCircle(16,16,4,p);for(int i=0;i<6;i++){double a=i*Math.PI/3;c.drawLine(16+(float)Math.cos(a)*5,16+(float)Math.sin(a)*5,16+(float)Math.cos(a)*9,16+(float)Math.sin(a)*9,p);}break;
            case "Bakım":c.drawLine(9,25,22,12,p);c.drawCircle(9,25,2,p);c.drawArc(new RectF(17,4,28,15),0,275,false,p);break;
            case "Sigorta/Kasko":Path shield=new Path();shield.moveTo(16,4);shield.lineTo(26,8);shield.lineTo(25,18);shield.quadTo(23,25,16,28);shield.quadTo(9,25,7,18);shield.lineTo(6,8);shield.close();c.drawPath(shield,p);c.drawLine(11,16,15,20,p);c.drawLine(15,20,22,12,p);break;
            case "Yakıt":c.drawRoundRect(6,5,20,27,2,2,p);c.drawRect(9,8,17,14,p);c.drawLine(4,27,22,27,p);c.drawLine(23,8,27,12,p);c.drawLine(27,12,27,23,p);c.drawLine(27,23,20,23,p);break;
            case "Galeri":c.drawRoundRect(4,6,28,26,3,3,p);c.drawCircle(21,12,2,p);Path hill=new Path();hill.moveTo(5,23);hill.lineTo(12,15);hill.lineTo(18,21);hill.lineTo(23,17);hill.lineTo(28,22);c.drawPath(hill,p);break;
            case "Hasar":Path alert=new Path();alert.moveTo(16,4);alert.lineTo(29,27);alert.lineTo(3,27);alert.close();c.drawPath(alert,p);c.drawLine(16,12,16,19,p);c.drawPoint(16,23,p);break;
            case "Muayene":c.drawRoundRect(5,7,27,27,3,3,p);c.drawLine(5,13,27,13,p);c.drawLine(10,4,10,10,p);c.drawLine(22,4,22,10,p);c.drawLine(11,20,15,23,p);c.drawLine(15,23,22,17,p);break;
            default:c.drawRoundRect(7,4,25,28,2,2,p);c.drawLine(11,10,21,10,p);c.drawLine(11,16,21,16,p);c.drawLine(11,22,18,22,p);break;
        }c.restore();
    }
}
