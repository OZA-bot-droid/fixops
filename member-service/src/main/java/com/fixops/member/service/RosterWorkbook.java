package com.fixops.member.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;

@Component
public class RosterWorkbook {
    public record RowData(int rowNo,String building,String unit,String name,String contact,String status,LocalDate moveIn,LocalDate moveOut,String error){}
    public record Parsed(byte[] bytes,List<RowData> rows){}
    private static final int MAX_BYTES=5*1024*1024;
    private static final long MAX_UNCOMPRESSED=32L*1024*1024;
    private static final String[] HEADERS={"동","호","성명","연락처","입주상태","입주일","퇴거일"};
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"ROSTER_VALIDATION_FAILED");}
    public Parsed parse(MultipartFile file){
        if(file.isEmpty()||file.getSize()>MAX_BYTES||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw invalid();
        String mime=file.getContentType();
        if(!Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream").contains(mime==null?"":mime))throw invalid();
        try {
            byte[] bytes=file.getBytes();
            if(bytes.length<4||bytes[0]!=80||bytes[1]!=75||bytes[2]!=3||bytes[3]!=4)throw invalid();
            preflight(bytes);
            try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
                if(workbook.getNumberOfSheets()!=1)throw invalid();
                Sheet sheet=workbook.getSheetAt(0);
                if(sheet.getLastRowNum()>2000)throw invalid();
                Row header=sheet.getRow(0);
                if(header==null||header.getLastCellNum()<5||header.getLastCellNum()>7)throw invalid();
                for(int i=0;i<header.getLastCellNum();i++)if(!HEADERS[i].equals(value(header,i)))throw invalid();
                List<RowData> result=new ArrayList<>();
                for(int i=1;i<=sheet.getLastRowNum();i++){
                    Row row=sheet.getRow(i);
                    if(row==null)continue;
                    if(row.getLastCellNum()>7)throw invalid();
                    for(Cell cell:row)if(cell.getCellType()==CellType.FORMULA)throw invalid();
                    String b=value(row,0),u=value(row,1),n=value(row,2),c=value(row,3),s=value(row,4);
                    if(b.isEmpty()&&u.isEmpty()&&n.isEmpty()&&c.isEmpty()&&s.isEmpty()&&value(row,5).isEmpty()&&value(row,6).isEmpty())continue;
                    try{
                        if(b.isBlank()||b.length()>40||u.isBlank()||u.length()>40||n.isBlank()||n.length()>100||c.isBlank()||c.length()>30)throw new IllegalArgumentException();
                        String status=switch(s){case "입주","ACTIVE"->"ACTIVE";case "퇴거","MOVED_OUT"->"MOVED_OUT";default->throw new IllegalArgumentException();};
                        LocalDate in=date(row,5),out=date(row,6);
                        if(in!=null&&out!=null&&out.isBefore(in))throw new IllegalArgumentException();
                        result.add(new RowData(i+1,b,u,n,c,status,in,out,null));
                    }catch(RuntimeException ex){result.add(new RowData(i+1,null,null,null,null,null,null,null,"INVALID_ROW"));}
                }
                if(result.isEmpty())throw invalid();
                return new Parsed(bytes,List.copyOf(result));
            }
        }catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw invalid();}
    }
    private void preflight(byte[] bytes)throws IOException{
        long total=0;int count=0;byte[] buffer=new byte[8192];boolean contentTypes=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(++count>200)throw invalid();
                String name=entry.getName().toLowerCase(Locale.ROOT);
                if(name.contains("vbaproject")||name.startsWith("xl/externallinks/")||name.startsWith("xl/embeddings/")||name.contains("../"))throw invalid();
                if(name.equals("[content_types].xml"))contentTypes=true;
                int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>MAX_UNCOMPRESSED)throw invalid();}
            }
        }
        if(!contentTypes)throw invalid();
    }
    private String value(Row row,int index){
        Cell cell=row.getCell(index);
        if(cell==null)return "";
        if(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR)throw invalid();
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    private LocalDate date(Row row,int index){
        Cell cell=row.getCell(index);
        if(cell==null||value(row,index).isEmpty())return null;
        if(cell.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(cell))return cell.getLocalDateTimeCellValue().toLocalDate();
        return LocalDate.parse(value(row,index));
    }
}
