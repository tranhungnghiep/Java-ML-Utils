/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * This script get the statistics and export paper data from MAS to the right file format used by text preprocessor.
 * Dataset: original MAS_finish.
 * 
 * Author:  THNghiep
 * Created: Dec 18, 2016
 */

-- Only export paper before or in 2010, with both title and abstract.
-- 1. MAS_doc.txt
-- 1.1. COUNT NUMBER OF PAPER WILL GET.
SELECT COUNT(*)
FROM PAPER P
WHERE P.YEAR <= 2010 AND P.TITLE IS NOT NULL AND P.ABSTRACT IS NOT NULL;
--> 1182744
-- 1.2. EXPORT: concatenate title and abstract as 2 sentences.
SELECT P.TITLE, P.ABSTRACT
FROM PAPER P
WHERE P.YEAR <= 2010 AND P.TITLE IS NOT NULL AND P.ABSTRACT IS NOT NULL
ORDER BY P.IDPAPER
INTO OUTFILE 'E:\\NghiepTH Working\\Data\\PTM\\MAS\\MAS_doc.txt'
FIELDS TERMINATED BY '. '
LINES TERMINATED BY '\r\n';
-- 2. MAS_ts.txt
-- 2.1. COUNT NUMBER OF UNIQUE TS, FIRST AND LAST TS, GET TS VOCABULARY SIZE.
SELECT COUNT(DISTINCT P.YEAR), MIN(P.YEAR), MAX(P.YEAR), MAX(P.YEAR) - MIN(P.YEAR) + 1
FROM PAPER P
WHERE P.YEAR <= 2010 AND P.TITLE IS NOT NULL AND P.ABSTRACT IS NOT NULL;
--> 60, 1951, 2010, 60
-- 2.2. EXPORT
SELECT P.YEAR
FROM PAPER P
WHERE P.YEAR <= 2010 AND P.TITLE IS NOT NULL AND P.ABSTRACT IS NOT NULL
ORDER BY P.IDPAPER
INTO OUTFILE 'E:\\NghiepTH Working\\Data\\PTM\\MAS\\MAS_ts.txt'
LINES TERMINATED BY '\r\n';