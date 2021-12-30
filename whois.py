from ipwhois import IPWhois

def whois(ip):
    data = IPWhois(ip, timeout = 15)
    data = data.lookup_whois()
    country = data['nets'][0]['country']
    city = data['nets'][0]['city']
    address = data['nets'][0]['address']
    return {'city': city, 'country': country, 'address': address}
    
