<%@ taglib prefix="s" uri="/struts-tags"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
         pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <link rel="stylesheet" type="text/css" href="css/model.css">
    <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
</head>
<body>
<p>Deassociate facebook account</p>
<s:form action="removeFacebook" method="post">
    <s:textfield cssClass="input" name="username" placeholder="Username:" /><br>
    <s:submit cssClass="btn" />
</s:form>
</body>
</html>